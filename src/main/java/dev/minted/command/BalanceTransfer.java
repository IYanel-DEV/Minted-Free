package dev.minted.command;

import dev.minted.api.MintedEconomy;
import dev.minted.ledger.LedgerService;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Balance migration: {@code /eco export <file>} writes every primary balance to
 * a plain-text file in the plugin's <em>exports</em> folder, and {@code /eco
 * import <file> confirm} sets every primary balance back from one. The format
 * is one "{@code uuid <space> balance}" line per account, prefixed by a comment
 * header, so the files are readable and hand-editable. Both commands run off
 * the main thread - exporting reads all of storage and importing creates an
 * account per line - and every line that cannot be applied is counted and
 * reported, never fatal.
 */
public final class BalanceTransfer {

    private final Plugin plugin;
    private final MintedEconomy economy;
    private final LedgerService ledger;

    public BalanceTransfer(Plugin plugin, MintedEconomy economy, LedgerService ledger) {
        this.plugin = plugin;
        this.economy = economy;
        this.ledger = ledger;
    }

    /** @return true if the sender was allowed to run a migration command */
    public boolean onCommand(CommandSender sender, String action, String[] rest, String label) {
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.RED + "Balance migration is console-only.");
            return true;
        }
        if (!sender.hasPermission("minted.eco")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (rest.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + action + " <file> [confirm]");
            return true;
        }
        final String fileName = safeName(rest[0]);
        if (fileName == null) {
            sender.sendMessage(ChatColor.RED + "Invalid file name - no folders and no special characters.");
            return true;
        }
        if ("export".equals(action)) {
            export(sender, fileName);
            return true;
        }
        if ("import".equals(action) && rest.length >= 2 && "confirm".equalsIgnoreCase(rest[1])) {
            importFile(sender, fileName);
            return true;
        }
        if ("import".equals(action)) {
            sender.sendMessage(ChatColor.RED + "This overwrites balances. Re-run with 'confirm' at the end.");
            return true;
        }
        return false;
    }

    private void export(final CommandSender sender, final String fileName) {
        sender.sendMessage(ChatColor.GRAY + "Exporting balances, please wait...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    Map<UUID, Double> balances = economy.snapshot();
                    File target = file(fileName);
                    Writer out = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(target), StandardCharsets.UTF_8));
                    try {
                        out.write("# Minted balance export - " + plugin.getDescription().getVersion() + "\n");
                        out.write("# <uuid> <balance>\n");
                        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
                            out.write(entry.getKey() + " " + entry.getValue() + "\n");
                        }
                    } finally {
                        out.close();
                    }
                    result = ChatColor.GREEN + "Exported " + balances.size() + " balances to "
                            + ChatColor.WHITE + relativeName(fileName) + ChatColor.GREEN + ".";
                } catch (RuntimeException e) {
                    result = ChatColor.RED + "Export failed: " + e.getMessage();
                } catch (IOException e) {
                    result = ChatColor.RED + "Export failed: " + e.getMessage();
                }
                final String finalResult = result;
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        sender.sendMessage(finalResult);
                    }
                });
            }
        });
    }

    private void importFile(final CommandSender sender, final String fileName) {
        sender.sendMessage(ChatColor.GRAY + "Importing balances, please wait...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    final File source = file(fileName);
                    if (!source.isFile()) {
                        result = ChatColor.RED + "No export file named '" + fileName + "' in the exports folder.";
                    } else {
                        int applied = 0;
                        int skipped = 0;
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                                new FileInputStream(source), StandardCharsets.UTF_8));
                        try {
                            String line;
                            while ((line = in.readLine()) != null) {
                                String trimmed = line.trim();
                                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                                    continue;
                                }
                                String[] parts = trimmed.split("\\s+");
                                if (parts.length != 2) {
                                    skipped++;
                                    continue;
                                }
                                UUID uuid;
                                double amount;
                                try {
                                    uuid = UUID.fromString(parts[0]);
                                    amount = Double.parseDouble(parts[1]);
                                } catch (RuntimeException notAPair) {
                                    skipped++;
                                    continue;
                                }
                                if (amount >= 0) {
                                    double previous = economy.getBalance(uuid);
                                    if (economy.setBalance(uuid, amount)) {
                                        ledger.record(uuid, amount - previous, "admin", "balance import");
                                        applied++;
                                    } else {
                                        skipped++;
                                    }
                                } else {
                                    skipped++;
                                }
                            }
                        } finally {
                            in.close();
                        }
                        result = ChatColor.GREEN + "Imported " + applied + " balances"
                                + (skipped > 0 ? " (" + skipped + " skipped)" : "") + " from "
                                + ChatColor.WHITE + relativeName(fileName) + ChatColor.GREEN + ".";
                    }
                } catch (RuntimeException e) {
                    result = ChatColor.RED + "Import failed: " + e.getMessage();
                } catch (IOException e) {
                    result = ChatColor.RED + "Import failed: " + e.getMessage();
                }
                final String finalResult = result;
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        sender.sendMessage(finalResult);
                    }
                });
            }
        });
    }

    private File file(String fileName) {
        File folder = new File(plugin.getDataFolder(), "exports");
        // Creates folder; File.mkdirs is safe on an existing directory.
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }
        return new File(folder, fileName);
    }

    private String relativeName(String fileName) {
        return "plugins/" + plugin.getDataFolder().getName() + "/exports/" + fileName;
    }

    // Migration files stay inside the exports folder: no separators, no dots
    // at the start, a sane length.
    private static String safeName(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 64) {
            return null;
        }
        if (raw.contains("/") || raw.contains("\\") || raw.contains("..")
                || raw.startsWith(".")) {
            return null;
        }
        return raw;
    }
}