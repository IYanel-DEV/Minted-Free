package dev.minted.vip;

import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.menu.HomeMenu;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The live {@code /<username>} command of one VIP: opens that VIP's player
 * shop for whoever types it. Eligibility is checked again on every use, so a
 * registration left behind by a failed unregister (an exotic command map) or a
 * VIP removed a moment ago degrades to a plain "no longer available" message
 * instead of serving someone who lost the perk.
 */
public final class VipShopCommand extends Command {

    /** Same door every shop screen uses, so the perk grants no extra reach. */
    static final String PERMISSION = "minted.shop.use";

    private final ShopContext ctx;
    private final VipShopCommands registry;
    private final UUID owner;

    VipShopCommand(ShopContext ctx, VipShopCommands registry, String playerName, UUID owner) {
        super(playerName);
        this.ctx = ctx;
        this.registry = registry;
        this.owner = owner;
        setDescription("Open " + playerName + "'s player shop.");
        setUsage("/" + playerName);
        setPermission(PERMISSION);
    }

    /** The VIP this command was registered for. */
    UUID owner() {
        return owner;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        if (args.length > 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label);
            return true;
        }
        if (!(sender instanceof Player)) {
            ctx.messages().send(sender, "vip.ingame-only");
            return true;
        }
        VipService vips = registry.vips();
        Shop shop = vips.isVip(owner) ? ctx.shops().playerShopOf(owner) : null;
        if (shop == null) {
            ctx.messages().send(sender, "vip.shop-gone");
            return true;
        }
        Player player = (Player) sender;
        new HomeMenu(ctx, shop, player).open(player);
        return true;
    }

    /** Zero-argument command: never suggests anything. */
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<String>();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return new ArrayList<String>();
    }
}