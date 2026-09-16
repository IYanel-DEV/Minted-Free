package dev.minted.shop;

import dev.minted.bank.MoneyFormat;
import dev.minted.bank.WalletService;
import dev.minted.compat.MaterialLookup;
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
    private final Market market;
    private final Messages messages;
    private final MoneyFormat format;
    private final ChatPrompt prompt;
    private final Design design;
    private final WalletService wallet;
    private final MaterialLookup materials;

    public ShopContext(ShopService shops, Trade trade, Market market, Messages messages,
                       MoneyFormat format, ChatPrompt prompt, Design design,
                       WalletService wallet, MaterialLookup materials) {
        this.shops = shops;
        this.trade = trade;
        this.market = market;
        this.messages = messages;
        this.format = format;
        this.prompt = prompt;
        this.design = design;
        this.wallet = wallet;
        this.materials = materials;
    }

    public ShopService shops() {
        return shops;
    }

    public Trade trade() {
        return trade;
    }

    public Market market() {
        return market;
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

    public Design design() {
        return design;
    }

    public WalletService wallet() {
        return wallet;
    }

    public MaterialLookup materials() {
        return materials;
    }
}
