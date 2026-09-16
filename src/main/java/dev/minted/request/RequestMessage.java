package dev.minted.request;

import dev.minted.bank.MoneyFormat;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Sends the payer the request notice with clickable Accept/Decline buttons.
 *
 * <p>Built with Spigot's chat-component API rather than raw NMS packets: it has
 * shipped with the server API since 1.8 and behaves the same across the whole
 * supported range, so no version shim is needed. If a stripped API ever lacks
 * it, the payer still gets a plain line telling them which commands to run.
 */
public final class RequestMessage {

    private RequestMessage() {
    }

    public static void send(Player payer, String requesterName, PaymentRequest request, MoneyFormat format) {
        String amount = format.format(request.getAmount());
        try {
            BaseComponent[] line = new ComponentBuilder(requesterName + " requests " + amount + "  ")
                    .color(net.md_5.bungee.api.ChatColor.GRAY)
                    .append("[Accept]").color(net.md_5.bungee.api.ChatColor.GREEN)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minted accept " + request.getId()))
                    .append("  ").color(net.md_5.bungee.api.ChatColor.GRAY)
                    .append("[Decline]").color(net.md_5.bungee.api.ChatColor.RED)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minted decline " + request.getId()))
                    .create();
            payer.spigot().sendMessage(line);
        } catch (Throwable ignored) {
            payer.sendMessage(ChatColor.GRAY + requesterName + " requests " + ChatColor.WHITE + amount);
            payer.sendMessage(ChatColor.GRAY + "Type " + ChatColor.GREEN + "/minted accept " + request.getId()
                    + ChatColor.GRAY + " or " + ChatColor.RED + "/minted decline " + request.getId());
        }
    }
}
