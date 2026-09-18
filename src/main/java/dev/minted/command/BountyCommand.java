package dev.minted.command;

import dev.minted.bounty.BountyService;
import dev.minted.gui.BountyMenu;
import dev.minted.gui.GuiContext;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BountyCommand implements CommandExecutor {

    private final GuiContext gui;

    public BountyCommand(GuiContext gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use bounties.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("minted.bounty")) {
            gui.messages().send(player, "bounty.no-permission");
            return true;
        }
        if (args.length == 0) {
            new BountyMenu(gui, 0).open(player);
            return true;
        }
        if (args.length < 2) {
            gui.messages().send(player, "bounty.usage", "label", label);
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            gui.messages().send(player, "bounty.invalid");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            gui.messages().send(player, "bounty.invalid");
            return true;
        }
        String note = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;
        BountyService bounties = gui.bounties();
        BountyService.Result result = bounties.place(target.getUniqueId(), player.getUniqueId(), amount, note);
        switch (result) {
            case OK:
                gui.messages().send(player, "bounty.placed", "amount", String.valueOf(amount), "target", target.getName());
                break;
            case SELF:
                gui.messages().send(player, "bounty.self");
                break;
            case MIN:
                gui.messages().send(player, "bounty.min", "min", String.valueOf(bounties.minAmount()));
                break;
            case MAX:
                gui.messages().send(player, "bounty.max", "max", String.valueOf(bounties.maxAmount()));
                break;
            case SHORT:
                gui.messages().send(player, "bounty.short", "amount", String.valueOf(amount));
                break;
            case NOT_READY:
                gui.messages().send(player, "bounty.not-ready");
                break;
            default:
                gui.messages().send(player, "bounty.invalid");
                break;
        }
        return true;
    }
}