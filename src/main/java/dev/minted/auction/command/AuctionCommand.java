package dev.minted.auction.command;

import dev.minted.auction.AuctionItem;
import dev.minted.auction.AuctionService;
import dev.minted.auction.menu.AuctionBrowseMenu;
import dev.minted.lang.Messages;
import dev.minted.shop.ShopContext;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /ah and /auction command handler.
 */
public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private final ShopContext ctx;
    private final AuctionService auctions;
    private final Messages messages;

    public AuctionCommand(ShopContext ctx, AuctionService auctions, Messages messages) {
        this.ctx = ctx;
        this.auctions = auctions;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "auction.players_only");
            return true;
        }

        Player player = (Player) sender;

        if (!auctions.isReady()) {
            messages.send(player, "auction.not_ready");
            return true;
        }

        if (args.length == 0) {
            new AuctionBrowseMenu(ctx, auctions, player, 0, "").open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
            case "sell":
                // Open create menu
                new AuctionBrowseMenu(ctx, auctions, player, 0, "").open(player);
                // The browse menu has a create button, but we could also open create directly
                break;
            case "my":
            case "mine":
                new AuctionBrowseMenu(ctx, auctions, player, 0, "").open(player);
                // Could open MyAuctionsMenu directly
                break;
            case "bids":
                new AuctionBrowseMenu(ctx, auctions, player, 0, "").open(player);
                break;
            case "expire":
                if (args.length < 2) {
                    messages.send(player, "auction.cmd.usage_expire");
                    return true;
                }
                try {
                    long id = Long.parseLong(args[1]);
                    auctions.expireAuction(id);
                    messages.send(player, "auction.cmd.expired", "id", args[1]);
                } catch (NumberFormatException e) {
                    messages.send(player, "auction.cmd.invalid_id");
                }
                break;
            case "cancel":
                if (args.length < 2) {
                    messages.send(player, "auction.cmd.usage_cancel");
                    return true;
                }
                try {
                    long id = Long.parseLong(args[1]);
                    AuctionItem auction = auctions.getAuction(id);
                    if (auction != null && auction.getSeller().equals(player.getUniqueId())) {
                        auctions.cancelAuction(player, id);
                    } else {
                        messages.send(player, "auction.cmd.not_yours");
                    }
                } catch (NumberFormatException e) {
                    messages.send(player, "auction.cmd.invalid_id");
                }
                break;
            case "reload":
                if (!player.hasPermission("minted.admin")) {
                    messages.send(player, "no_permission");
                    return true;
                }
                auctions.reload();
                messages.send(player, "auction.cmd.reloaded");
                break;
            default:
                messages.send(player, "auction.cmd.usage");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "my", "bids", "expire", "cancel", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("expire") || args[0].equalsIgnoreCase("cancel"))) {
            // Could add auction IDs here
        }
        return filter(completions, args[args.length - 1]);
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}