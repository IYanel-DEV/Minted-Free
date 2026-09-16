package dev.minted.shop;

import dev.minted.bank.MoneyFormat;
import dev.minted.gui.ChatPrompt;
import dev.minted.gui.theme.Design;
import dev.minted.lang.Messages;

/**
 * The shared dependencies the shop menus and command need, passed as one value
 * so their constructors stay about their own subject (a shop, an item, a page).
 * Mirrors {@code dev.minted.gui.GuiContext} for the shop side of the plugin.
 */
public final class ShopContext {

    private final ShopService shops;
    private final Trade trade;
    private final Messages messages;
    private final MoneyFormat format;
    private final ChatPrompt prompt;
    private final Design design;

    public ShopContext(ShopService shops, Trade trade, Messages messages,
                       MoneyFormat format, ChatPrompt prompt, Design design) {
        this.shops = shops;
        this.trade = trade;
        this.messages = messages;
        this.format = format;
        this.prompt = prompt;
        this.design = design;
    }

    public Design design() {
        return design;
    }

    public ShopService shops() {
        return shops;
    }

    public Trade trade() {
        return trade;
    }

    public Messages messages() {
        return messages;
    }

    public MoneyFormat format() {
        return format;
    }

    public ChatPrompt prompt() {
        return prompt;
    }
}
