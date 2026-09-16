package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.ShopContext;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * A one-click "are you sure" gate for the money- and stock-moving actions
 * (collect, withdraw, a big buy). Confirm runs the action; cancel returns.
 */
public final class ConfirmMenu extends Menu {

    private final ShopContext ctx;
    private final String prompt;
    private final List<String> detail;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;

    public ConfirmMenu(ShopContext ctx, String title, String prompt, List<String> detail,
                       Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        super(Design.title(Design.Accent.COMMUNITY, title), 3);
        this.ctx = ctx;
        this.prompt = prompt;
        this.detail = detail;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));
        set(4, Icon.of(Material.PAPER, Design.HEADING + "" + org.bukkit.ChatColor.BOLD + prompt,
                Design.lore(null, detail, null)), null);
        set(11, d.confirm("Confirm"), onConfirm);
        set(15, d.cancel(), onCancel);
        fillEmpty(d.filler());
    }
}
