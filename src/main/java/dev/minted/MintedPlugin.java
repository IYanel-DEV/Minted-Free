package dev.minted;

import dev.minted.backend.DatabaseSettings;
import dev.minted.backend.HikariPool;
import dev.minted.backend.SqlDialect;
import dev.minted.backend.SqlStorageProvider;
import dev.minted.bank.AccountListener;
import dev.minted.bank.AccountSaveTask;
import dev.minted.bank.BankService;
import dev.minted.bank.CombatLock;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;
import dev.minted.bank.WalletService;
import dev.minted.banknote.BanknoteListener;
import dev.minted.banknote.BanknoteManager;
import dev.minted.banknote.BanknoteParser;
import dev.minted.banknote.NoteInventory;
import dev.minted.command.BalanceCommand;
import dev.minted.command.BankCommand;
import dev.minted.command.CommandManager;
import dev.minted.command.PayCommand;
import dev.minted.command.SellCommand;
import dev.minted.compat.Glass;
import dev.minted.compat.MaterialLookup;
import dev.minted.compat.ServerVersion;
import dev.minted.gui.ChatPrompt;
import dev.minted.gui.GuiContext;
import dev.minted.gui.InteractionListener;
import dev.minted.gui.MenuListener;
import dev.minted.gui.theme.Design;
import dev.minted.lang.Messages;
import dev.minted.request.RequestService;
import dev.minted.sound.SoundFX;
import dev.minted.shop.Market;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopService;
import dev.minted.shop.Trade;
import dev.minted.shop.command.ShopCommand;
import dev.minted.shop.command.ShopTabCompleter;
import dev.minted.shop.storage.ShopDao;

import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minted plugin entry point.
 *
 * <p>Only lifecycle wiring lives here. Each subsystem is a self-contained
 * class; this class just builds them and connects them to Bukkit. Storage is
 * opened on an async task so a slow database never stalls server startup. The
 * wallet and the bank are two instances of the same {@link EconomyService},
 * each backed by its own table.
 */
public final class MintedPlugin extends JavaPlugin {

    private static final long SAVE_INTERVAL_TICKS = 20L * 60L;
    private static final long REQUEST_SWEEP_TICKS = 20L * 20L;

    private static MintedPlugin instance;

    private ServerVersion serverVersion;
    private HikariPool pool;
    private SqlStorageProvider walletStorage;
    private SqlStorageProvider bankStorage;
    private EconomyService walletEconomy;
    private EconomyService bankEconomy;
    private RequestService requestService;
    private ShopService shopService;

    public static MintedPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.serverVersion = ServerVersion.detect();
        if (!serverVersion.isSupported()) {
            getLogger().severe("Minted requires Minecraft 1.8 or newer; detected " + serverVersion + ". Disabling.");
            setEnabled(false);
            return;
        }

        saveDefaultConfig();
        wire();

        getLogger().info("Minted " + getDescription().getVersion() + " enabled (server " + serverVersion + ").");
    }

    @Override
    public void onDisable() {
        if (walletEconomy != null && walletEconomy.isReady()) {
            walletEconomy.saveAllBlocking();
        }
        if (bankEconomy != null && bankEconomy.isReady()) {
            bankEconomy.saveAllBlocking();
        }
        if (shopService != null) {
            shopService.shutdown();
        }
        if (pool != null) {
            pool.close();
        }
        instance = null;
        getLogger().info("Minted disabled.");
    }

    private void wire() {
        DatabaseSettings settings = DatabaseSettings.from(getConfig(), getDataFolder());
        SqlDialect dialect = SqlDialect.fromId(settings.getType());
        this.pool = new HikariPool(settings, dialect);
        this.walletStorage = new SqlStorageProvider(pool, dialect, "minted_accounts");
        this.bankStorage = new SqlStorageProvider(pool, dialect, "bank_accounts");

        double starting = getConfig().getDouble("economy.starting-balance", 0);
        double max = getConfig().getDouble("economy.max-balance", 1_000_000_000);
        boolean physical = getConfig().getBoolean("economy.physical", true);
        this.walletEconomy = new EconomyService(this, walletStorage, starting, max);
        this.bankEconomy = new EconomyService(this, bankStorage, 0, max);
        BankService bankService = new BankService(walletEconomy, bankEconomy, max);

        MoneyFormat format = MoneyFormat.from(getConfig());
        BanknoteManager banknotes = new BanknoteManager(new BanknoteParser(format, serverVersion), denominations());
        NoteInventory noteInventory = new NoteInventory(banknotes);
        WalletService walletService = new WalletService(physical, walletEconomy, noteInventory);

        Messages messages = Messages.load(this);
        this.requestService = new RequestService(this, walletService, format,
                getConfig().getLong("bank.request-expiry-seconds", 60));

        CombatLock combatLock = combatLock();

        MaterialLookup materials = new MaterialLookup(serverVersion);
        Design design = new Design(new Glass(serverVersion));
        SoundFX sounds = new SoundFX(getConfig().getConfigurationSection("sounds"));

        ChatPrompt chatPrompt = new ChatPrompt(this);
        GuiContext gui = new GuiContext(walletEconomy, bankEconomy, bankService, walletService, noteInventory,
                format, chatPrompt, requestService, presets(), banknotes, messages, combatLock, design, sounds);

        this.shopService = new ShopService(this, new ShopDao(pool, dialect), serverVersion, materials);
        Trade trade = new Trade(walletService, bankEconomy, format, messages);
        Market market = new Market(shopService, walletService, banknotes, format, messages);
        ShopContext shopContext = new ShopContext(shopService, trade, market, messages, format, chatPrompt,
                design, walletService, materials);

        registerListeners(chatPrompt, gui, banknotes, noteInventory, format, messages, physical, combatLock);

        new CommandManager(this, gui, requestService).register();
        setExecutor("balance", new BalanceCommand(walletService, format));
        setExecutor("pay", new PayCommand(walletService, format, sounds));
        setExecutor("bank", new BankCommand(bankService, bankEconomy, banknotes, noteInventory, gui, format,
                messages, physical, combatLock));
        setExecutor("sell", new SellCommand(shopContext, banknotes, getConfig().getString("shops.global", "Spawn")));
        registerShopCommand(shopContext);

        openStorageAsync();
    }

    private void registerListeners(ChatPrompt chatPrompt, GuiContext gui, BanknoteManager banknotes,
                                   NoteInventory noteInventory, MoneyFormat format, Messages messages,
                                   boolean physical, CombatLock combatLock) {
        PluginManager events = getServer().getPluginManager();
        events.registerEvents(new AccountListener(walletEconomy), this);
        events.registerEvents(new AccountListener(bankEconomy), this);
        events.registerEvents(new MenuListener(), this);
        events.registerEvents(chatPrompt, this);
        events.registerEvents(requestService, this);
        events.registerEvents(combatLock, this);
        events.registerEvents(new InteractionListener(gui, requireSneak()), this);
        events.registerEvents(new BanknoteListener(walletEconomy, bankEconomy, banknotes, noteInventory,
                format, messages, physical, combatLock), this);
    }

    // Combat lock config: hit-triggered deposit block. Disabled -> inert.
    private CombatLock combatLock() {
        boolean enabled = getConfig().getBoolean("bank.hit-lock.enabled", true);
        int seconds = getConfig().getInt("bank.hit-lock.seconds", 8);
        boolean playersOnly = "player".equalsIgnoreCase(getConfig().getString("bank.hit-lock.trigger", "any"));
        return new CombatLock(enabled, seconds, playersOnly);
    }

    // Physical-cash denominations for minting. Zero or negative values would make
    // the note-splitting divide by zero, so they are dropped with a warning; an
    // empty or all-invalid list falls back to the big ladder that config.yml
    // ships (saveDefaultConfig never rewrites an existing file, so a jar-only
    // upgrade keeps its old config and would otherwise be stuck on tiny notes).
    private List<Double> denominations() {
        List<Double> valid = new ArrayList<Double>();
        for (Double value : getConfig().getDoubleList("money.denominations")) {
            if (value != null && value > 0) {
                valid.add(value);
            } else {
                getLogger().warning("Ignoring invalid money.denomination '" + value + "' (must be greater than zero).");
            }
        }
        if (valid.isEmpty()) {
            return Arrays.asList(1.0, 5.0, 10.0, 50.0, 100.0, 1_000.0, 10_000.0, 100_000.0,
                    1_000_000.0, 10_000_000.0, 100_000_000.0, 1_000_000_000.0);
        }
        return valid;
    }

    // storage.open() touches disk/network, so both tables are opened off the main
    // thread. Once they succeed we flip both services ready, load anyone already
    // online (covers /reload) and start the batch savers.
    private void openStorageAsync() {
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    walletStorage.open();
                    bankStorage.open();
                } catch (RuntimeException e) {
                    getLogger().severe("Minted could not open its database: " + e.getMessage());
                    return;
                }
                getServer().getScheduler().runTask(MintedPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        finishStartup();
                    }
                });
            }
        });
    }

    private void finishStartup() {
        walletEconomy.markReady();
        bankEconomy.markReady();
        for (Player player : getServer().getOnlinePlayers()) {
            walletEconomy.load(player.getUniqueId(), null);
            bankEconomy.load(player.getUniqueId(), null);
        }
        shopService.initialize();
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                new AccountSaveTask(walletEconomy, walletStorage), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                new AccountSaveTask(bankEconomy, bankStorage), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                requestService.purgeExpired();
            }
        }, REQUEST_SWEEP_TICKS, REQUEST_SWEEP_TICKS);
    }

    private double[] presets() {
        List<Double> configured = getConfig().getDoubleList("bank.presets");
        if (configured.isEmpty()) {
            return new double[] {50, 100, 500};
        }
        double[] values = new double[configured.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = configured.get(i);
        }
        return values;
    }

    private boolean requireSneak() {
        return !"right_click".equalsIgnoreCase(getConfig().getString("player_interact.action", "shift_right_click"));
    }

    private void setExecutor(String name, CommandExecutor executor) {
        if (getCommand(name) == null) {
            getLogger().warning("Command /" + name + " is missing from plugin.yml");
            return;
        }
        getCommand(name).setExecutor(executor);
    }

    private void registerShopCommand(ShopContext shopContext) {
        if (getCommand("eshop") == null) {
            getLogger().warning("Command /eshop is missing from plugin.yml");
            return;
        }
        getCommand("eshop").setExecutor(new ShopCommand(shopContext));
        getCommand("eshop").setTabCompleter(new ShopTabCompleter(shopContext));
    }

    public ServerVersion getServerVersion() {
        return serverVersion;
    }

    public EconomyService getEconomyService() {
        return walletEconomy;
    }
}
