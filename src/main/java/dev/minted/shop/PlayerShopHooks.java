package dev.minted.shop;

/**
 * Lifecycle callbacks for player-owned shops, fired on the main thread by
 * {@link ShopService}. The VIP {@code /<username>} shop commands hang off
 * these, so they stay in step with shop creation, deletion and the initial
 * load no matter which command touched the shop.
 */
public interface PlayerShopHooks {

    /** A player shop was just created and queued for persistence. */
    void onPlayerShopCreated(Shop shop);

    /** A player shop was just deleted. */
    void onPlayerShopDeleted(Shop shop);

    /** The shop model finished loading (startup or {@code /eshop reload}). */
    void onShopsLoaded();
}