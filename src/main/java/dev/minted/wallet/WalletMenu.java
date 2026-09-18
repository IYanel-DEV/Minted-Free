package dev.minted.wallet;

import dev.minted.compat.Heads;
import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.lang.Messages;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The wallet's own menu: shows every denomination held, lets the player take
 * notes back out one by one or in bulk, deposit every banknote from the
 * inventory in one click, or empty the wallet.
 *
 * <p>This is a read-mostly menu - the notes never sit in the GUI itself, they
 * live in the wallet item's NBT. Every action reads the contents from the held
 * item, updates them, writes them back, and refreshes in place, so closing the
 * menu can never lose money.
 */
public final class WalletMenu extends Menu {

    private final WalletManager wallets;
    private final Player player;

    public WalletMenu(WalletManager wallets, Player player) {
        super(Design.title(Design.Accent.NEUTRAL, "Minted | Wallet"), 5);
        this.wallets = wallets;
        this.player = player;
    }

    @Override
    protected void build() {
        Design design = wallets.design();
        frame(design.border(Design.Accent.NEUTRAL));

        set(0, balance(design), null);
        set(36, Icon.of(Heads.icon(Heads.homeSkin(), Material.CHEST),
                        Design.HEADING + "" + ChatColor.BOLD + "Deposit notes",
                        Design.lore("Moves the banknotes you carry", null, "into this wallet.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player p) {
                        deposit(p);
                    }
                });
        set(37, Icon.of(Heads.icon(Heads.noneSkin(), Material.BARRIER),
                        Design.OUT + "" + ChatColor.BOLD + "Empty wallet",
                        Design.lore("Takes everything out of the wallet.", null, "into your inventory.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player p) {
                        empty(p);
                    }
                });
        set(44, design.close(), new Consumer<Player>() {
            @Override
            public void accept(Player p) {
                p.closeInventory();
            }
        });

        ItemStack hand = wallets.held(player);
        if (hand == null) {
            return;
        }
        WalletContents contents = wallets.contents(hand);
        List<Map.Entry<Long, Integer>> rows = sortedRows(contents.snapshot());
        if (rows.isEmpty()) {
            set(22, Icon.of(Material.PAPER, Design.HEADING + "" + ChatColor.BOLD + "Empty",
                            Design.lore("No notes inside yet.", null, "Deposit some cash below.")), null);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            int slot = contentSlot(i);
            if (slot < 0) {
                break;
            }
            final long cents = rows.get(i).getKey();
            final int count = rows.get(i).getValue();
            setClick(slot, rowIcon(cents, count), new ClickHandler() {
                @Override
                public void click(Player p, ClickType type) {
                    withdraw(p, cents, type.isRightClick() ? count : 1, count);
                }
            });
        }
    }

    private ItemStack balance(Design design) {
        double value = wallets.value(player);
        int count = 0;
        ItemStack hand = wallets.held(player);
        if (hand != null) {
            count = wallets.contents(hand).noteCount();
        }
        String hint = count == 1 ? "1 note inside" : count + " notes inside";
        return Icon.of(Heads.icon(Heads.walletSkin(), Material.GOLD_INGOT),
                Design.MONEY + "" + ChatColor.BOLD + "Wallet",
                Design.lore(null, Collections.singletonList(Design.HINT + wallets.format().format(value)),
                        Design.HINT + hint));
    }

    private ItemStack rowIcon(long cents, int count) {
        return Icon.of(Material.PAPER, Design.MONEY + "" + ChatColor.BOLD + wallets.format().brief(cents / 100.0),
                Design.lore(null, Collections.singletonList(Design.LABEL + "Held: " + Design.HINT + "x" + count),
                        "Left take one | Right take all"));
    }

    // Interior grid slots (rows 1-3, columns 1-7), left to right, top to bottom.
    private int contentSlot(int index) {
        int row = 1 + index / 7;
        int col = 1 + index % 7;
        return row >= 4 ? -1 : row * 9 + col;
    }

    private List<Map.Entry<Long, Integer>> sortedRows(Map<Long, Integer> snapshot) {
        List<Map.Entry<Long, Integer>> rows = new ArrayList<Map.Entry<Long, Integer>>(snapshot.entrySet());
        Collections.sort(rows, new Comparator<Map.Entry<Long, Integer>>() {
            @Override
            public int compare(Map.Entry<Long, Integer> a, Map.Entry<Long, Integer> b) {
                return Long.compare(b.getKey(), a.getKey());
            }
        });
        return rows;
    }

    private void deposit(Player player) {
        double[] result = wallets.depositInventory(player);
        Messages messages = wallets.messages();
        if (result[0] <= 0) {
            messages.send(player, "wallet.deposit-none");
        } else if (result[1] > 0) {
            messages.send(player, "wallet.deposit-partial",
                    "banked", wallets.format().format(result[0]),
                    "left", wallets.format().format(result[1]));
        } else {
            messages.send(player, "wallet.deposit", "amount", wallets.format().format(result[0]));
        }
        refresh();
    }

    private void withdraw(Player player, long cents, int want, int held) {
        WalletManager.Draw draw = wallets.withdraw(player, cents, Math.min(want, held));
        Messages messages = wallets.messages();
        if (draw.count <= 0) {
            return;
        }
        String note = wallets.format().format(draw.value);
        messages.send(player, draw.count == 1 ? "wallet.withdraw-one" : "wallet.withdraw-all",
                "count", String.valueOf(draw.count), "note", note);
        if (draw.dropped) {
            messages.send(player, "wallet.dropped");
        }
        refresh();
    }

    private void empty(Player player) {
        WalletManager.Draw draw = wallets.empty(player);
        Messages messages = wallets.messages();
        if (draw.count > 0) {
            messages.send(player, "wallet.empty");
            if (draw.dropped) {
                messages.send(player, "wallet.dropped");
            }
        }
        refresh();
    }
}