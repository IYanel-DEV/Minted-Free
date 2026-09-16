package dev.minted;

import dev.minted.backend.DatabaseSettings;
import dev.minted.backend.HikariPool;
import dev.minted.backend.SqlDialect;
import dev.minted.backend.SqlStorageProvider;
import dev.minted.backend.StorageProvider;
import dev.minted.bank.AccountListener;
import dev.minted.bank.AccountSaveTask;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;
import dev.minted.banknote.BanknoteListener;
import dev.minted.banknote.BanknoteManager;
import dev.minted.banknote.BanknoteParser;
import dev.minted.command.BalanceCommand;
import dev.minted.command.CommandManager;
import dev.minted.command.PayCommand;
import dev.minted.compat.ServerVersion;

import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minted plugin entry point.
 *
 * <p>Only lifecycle wiring lives here. Each subsystem is a self-contained
 * class; this class just builds them and connects them to Bukkit. Storage is
 * opened on an async task so a slow database never stalls server startup.
 */
public final class MintedPlugin extends JavaPlugin {

    private static final long SAVE_INTERVAL_TICKS = 20L * 60L;

    private static MintedPlugin instance;

    private ServerVersion serverVersion;
    private CommandManager commandManager;
    private StorageProvider storage;
    private EconomyService economyService;

    public static MintedPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.serverVersion = ServerVersion.detect();
        if (!serverVersion.isSupported()) {
            getLogger().severe("Minted requires Minecraft 1.8 or newer; detected " + serverVersion + ". Disabling.");
            setEnabled(false);
            return;
        }

        saveDefaultConfig();

        this.commandManager = new CommandManager(this);
        this.commandManager.register();

        wireEconomy();

        getLogger().info("Minted " + getDescription().getVersion() + " enabled (server " + serverVersion + ").");
    }

    @Override
    public void onDisable() {
        if (economyService != null && economyService.isReady()) {
            economyService.saveAllBlocking();
        }
        if (storage != null) {
            storage.close();
        }
        instance = null;
        getLogger().info("Minted disabled.");
    }

    private void wireEconomy() {
        DatabaseSettings settings = DatabaseSettings.from(getConfig(), getDataFolder());
        SqlDialect dialect = SqlDialect.fromId(settings.getType());
        this.storage = new SqlStorageProvider(new HikariPool(settings, dialect), dialect);

        double starting = getConfig().getDouble("economy.starting-balance", 0);
        double max = getConfig().getDouble("economy.max-balance", 1_000_000_000);
        this.economyService = new EconomyService(this, storage, starting, max);

        MoneyFormat format = MoneyFormat.from(getConfig());
        BanknoteManager banknotes = new BanknoteManager(new BanknoteParser(format));

        getServer().getPluginManager().registerEvents(new AccountListener(economyService), this);
        getServer().getPluginManager().registerEvents(
                new BanknoteListener(economyService, banknotes, format), this);

        setExecutor("balance", new BalanceCommand(economyService, format));
        setExecutor("pay", new PayCommand(economyService, format));

        openStorageAsync();
    }

    // Storage.open() touches disk/network, so it runs off the main thread. Once
    // it succeeds we flip the service ready, load anyone already online (covers
    // /reload) and start the batch saver.
    private void openStorageAsync() {
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    storage.open();
                } catch (RuntimeException e) {
                    getLogger().severe("Minted could not open its database: " + e.getMessage());
                    return;
                }
                getServer().getScheduler().runTask(MintedPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        finishStartup();
                    }
                });
            }
        });
    }

    private void finishStartup() {
        economyService.markReady();
        for (Player player : getServer().getOnlinePlayers()) {
            economyService.load(player.getUniqueId(), null);
        }
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                new AccountSaveTask(economyService, storage), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    private void setExecutor(String name, CommandExecutor executor) {
        if (getCommand(name) == null) {
            getLogger().warning("Command /" + name + " is missing from plugin.yml");
            return;
        }
        getCommand(name).setExecutor(executor);
    }

    public ServerVersion getServerVersion() {
        return serverVersion;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }
}
