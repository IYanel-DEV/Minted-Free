package dev.minted.shop;

import java.util.Locale;

/**
 * Which kind of shop a row is. A {@code GLOBAL} shop is admin-managed with an
 * infinite item supply and money to/from the void; the single {@code COMMUNITY}
 * marketplace holds real player stock; a {@code PLAYER} shop is one player's
 * own personal storefront over the same real stock. Old rows have no type
 * column and read as {@link #GLOBAL}.
 */
public enum ShopType {

    GLOBAL,
    COMMUNITY,
    PLAYER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ShopType fromId(String id) {
        if (id != null && "community".equalsIgnoreCase(id.trim())) {
            return COMMUNITY;
        }
        if (id != null && "player".equalsIgnoreCase(id.trim())) {
            return PLAYER;
        }
        return GLOBAL;
    }
}
