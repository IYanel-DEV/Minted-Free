package dev.minted.shop;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A shop and the items laid out across its pages. Items are addressed by
 * {@code (page, slot)}; a page is considered to exist once it holds an item, so
 * {@link #pageCount()} grows and shrinks with the contents rather than being a
 * stored field. The icon and name are shown in the browse and shop menus.
 */
public final class Shop {

    /** Item slots per page: the top five rows. The bottom row is navigation. */
    public static final int SLOTS_PER_PAGE = 45;

    private final int id;
    private String name;
    private ItemStack icon;
    private Currency currency;
    private final ShopType type;

    // Keyed by page * SLOTS_PER_PAGE + slot, sorted so iteration is page-then-slot.
    private final TreeMap<Integer, ShopItem> items = new TreeMap<Integer, ShopItem>();

    public Shop(int id, String name, ItemStack icon, Currency currency) {
        this(id, name, icon, currency, ShopType.GLOBAL);
    }

    public Shop(int id, String name, ItemStack icon, Currency currency, ShopType type) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.currency = currency;
        this.type = type;
    }

    public ShopType getType() {
        return type;
    }

    public boolean isCommunity() {
        return type == ShopType.COMMUNITY;
    }

    /** The next free (page, slot) address, or -1 when full - used by community listings. */
    public int firstFreeAddress() {
        for (int address = 0; address < SLOTS_PER_PAGE * 64; address++) {
            if (!items.containsKey(address)) {
                return address;
            }
        }
        return -1;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ItemStack getIcon() {
        return icon.clone();
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    ItemStack rawIcon() {
        return icon;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public ShopItem itemAt(int page, int slot) {
        return items.get(key(page, slot));
    }

    public void put(ShopItem item) {
        items.put(key(item.getPage(), item.getSlot()), item);
    }

    public void removeAt(int page, int slot) {
        items.remove(key(page, slot));
    }

    public Collection<ShopItem> allItems() {
        return new ArrayList<ShopItem>(items.values());
    }

    public List<ShopItem> itemsOnPage(int page) {
        List<ShopItem> onPage = new ArrayList<ShopItem>();
        for (Map.Entry<Integer, ShopItem> entry : items.entrySet()) {
            if (entry.getValue().getPage() == page) {
                onPage.add(entry.getValue());
            }
        }
        return onPage;
    }

    /** Distinct category tags in stored order; excludes uncategorised items. */
    public Set<String> categories() {
        Set<String> names = new LinkedHashSet<String>();
        for (ShopItem item : items.values()) {
            if (item.getCategory() != null) {
                names.add(item.getCategory());
            }
        }
        return names;
    }

    /** At least one page; otherwise one past the highest page that holds an item. */
    public int pageCount() {
        if (items.isEmpty()) {
            return 1;
        }
        return items.lastKey() / SLOTS_PER_PAGE + 1;
    }

    private int key(int page, int slot) {
        return page * SLOTS_PER_PAGE + slot;
    }
}
