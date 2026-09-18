package dev.minted.gui;

import dev.minted.bounty.Bounty;
import dev.minted.bounty.BountyService;
import dev.minted.compat.Heads;
import dev.minted.gui.theme.Design;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The server-wide bounty board: every player with an open bounty, one row per
 * target with the running reward total. Clicking a row the viewer placed money
 * on (any row, for admins) opens the refund page, where each posted bounty can
 * be pulled back into its placer's bank.
 */
public final class BountyMenu extends Menu {

    private final GuiContext ctx;
    private final int page;

    public BountyMenu(GuiContext ctx, int page) {
        super(Design.title(Design.Accent.NEUTRAL, "Active Bounties"), 6);
        this.ctx = ctx;
        this.page = page;
    }

    @Override
    protected void build() {
        BountyService bounties = ctx.bounties();
        Design design = ctx.design();
        ItemStack pane = design.border(Design.Accent.NEUTRAL);
        frame(pane);
        fillEmpty(pane);

        Map<UUID, Double> aggregatedAmounts = new HashMap<UUID, Double>();
        Map<UUID, String> aggregatedNotes = new HashMap<UUID, String>();
        for (Bounty bounty : bounties.listed()) {
            aggregatedAmounts.put(bounty.target(),
                    aggregatedAmounts.getOrDefault(bounty.target(), 0.0) + bounty.amount());
            if (!aggregatedNotes.containsKey(bounty.target()) && bounty.note() != null) {
                aggregatedNotes.put(bounty.target(), bounty.note());
            }
        }

        List<UUID> sortedTargets = aggregatedAmounts.entrySet().stream()
            .sorted(Comparator.comparing((Map.Entry<UUID, Double> e) -> Bukkit.getOfflinePlayer(e.getKey()).isOnline()).reversed()
            .thenComparing(e -> Bukkit.getOfflinePlayer(e.getKey()).getName()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        int pageSize = 28;
        int maxPages = (int) Math.ceil((double) sortedTargets.size() / pageSize);
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, sortedTargets.size());

        for (int i = startIndex; i < endIndex; i++) {
            final UUID targetId = sortedTargets.get(i);
            double totalAmount = aggregatedAmounts.get(targetId);
            OfflinePlayer player = Bukkit.getOfflinePlayer(targetId);

            ItemStack head = Heads.skull();
            if (head != null) {
                ItemMeta meta = head.getItemMeta();
                String status = player.isOnline() ? Design.IN + "Online" : Design.OUT + "Offline";
                // We use a generic skull to avoid the synchronous API call that hangs the server.
                meta.setDisplayName(Design.HEADING + player.getName());
                List<String> lore = new ArrayList<String>();
                lore.add(Design.LABEL + "Status: " + status);
                lore.add(Design.MONEY + "Total Bounty: " + totalAmount);
                String note = aggregatedNotes.get(targetId);
                if (note != null) lore.add(Design.LABEL + "Note: " + Design.HINT + note);
                lore.add("");
                lore.add(Design.HINT + "Click to refund a bounty you placed.");
                meta.setLore(Design.lore(null, lore, null));
                head.setItemMeta(meta);
                setClick(10 + (i - startIndex) + (2 * ((i - startIndex) / 7)), head, new ClickHandler() {
                    @Override
                    public void click(Player clicker, ClickType type) {
                        BountyService service = ctx.bounties();
                        boolean admin = clicker.hasPermission("minted.admin");
                        List<Bounty> refundable = new ArrayList<Bounty>();
                        for (Bounty bounty : service.pendingOn(targetId)) {
                            if (admin || bounty.placer().equals(clicker.getUniqueId())) {
                                refundable.add(bounty);
                            }
                        }
                        if (refundable.isEmpty()) {
                            ctx.messages().send(clicker, "bounty.none-mine");
                            return;
                        }
                        new BountyRefundMenu(ctx, targetId, clicker.getUniqueId(), admin).open(clicker);
                    }
                });
            }
        }

        if (page > 0) setClick(45, design.prev(), (p, t) -> new BountyMenu(ctx, page - 1).open(p));
        if (page < maxPages - 1) setClick(53, design.next(), (p, t) -> new BountyMenu(ctx, page + 1).open(p));
    }
}