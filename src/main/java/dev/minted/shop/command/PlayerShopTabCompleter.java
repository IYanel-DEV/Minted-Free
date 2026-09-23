package dev.minted.shop.command;

import dev.minted.shop.ShopContext;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tab completion for {@code /pshop}: the subcommand names on the first token,
 * then player shop names (for {@code open}/{@code delete}) and nothing further.
 */
public final class PlayerShopTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS =
            Arrays.asList("create", "add", "addstack", "sell", "my", "browse", "open", "delete");

    private final ShopContext ctx;

    public PlayerShopTabCompleter(ShopContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && ("open".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<String>();
            for (dev.minted.shop.Shop shop : ctx.shops().all()) {
                if (shop.isPlayerShop()) {
                    names.add(shop.getName());
                }
            }
            return complete(names, args[1]);
        }
        return new ArrayList<String>();
    }

    private List<String> complete(List<String> options, String prefix) {
        List<String> out = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
                out.add(option);
            }
        }
        return out;
    }
}