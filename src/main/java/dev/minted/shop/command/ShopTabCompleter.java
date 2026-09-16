package dev.minted.shop.command;

import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tab completion for {@code /eshop}: shop names for everyone, plus the admin
 * subcommands and their arguments for anyone holding {@code minted.shop.admin}.
 */
public final class ShopTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS =
            Arrays.asList("create", "delete", "edit", "reload", "setcurrency");
    private static final List<String> TARGETED = Arrays.asList("delete", "edit", "setcurrency");

    private final ShopContext ctx;

    public ShopTabCompleter(ShopContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(args[0], firstArgs(sender));
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && TARGETED.contains(first)) {
            return matching(args[1], shopNames());
        }
        if (args.length == 3 && "setcurrency".equals(first)) {
            return matching(args[2], Arrays.asList("wallet", "bank"));
        }
        return new ArrayList<String>();
    }

    private List<String> firstArgs(CommandSender sender) {
        List<String> options = shopNames();
        if (sender.hasPermission("minted.shop.admin")) {
            options.addAll(SUBCOMMANDS);
        }
        return options;
    }

    private List<String> shopNames() {
        List<String> names = new ArrayList<String>();
        for (Shop shop : ctx.shops().all()) {
            names.add(shop.getName());
        }
        return names;
    }

    private List<String> matching(String typed, List<String> options) {
        List<String> result = new ArrayList<String>();
        String lower = typed.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
