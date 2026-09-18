package dev.minted;

import dev.minted.backend.DatabaseSettings;
import dev.minted.backend.HikariPool;
import dev.minted.backend.LoansDao;
import dev.minted.backend.NamesDao;
import dev.minted.backend.NamesListener;
import dev.minted.backend.SaleDao;
import dev.minted.backend.SqlDialect;
import dev.minted.backend.SqlStorageProvider;
import dev.minted.backend.StorageException;
import dev.minted.backend.StorageProvider;
import dev.minted.backend.StatsDao;
import dev.minted.bank.AccountListener;
import dev.minted.bank.AccountSaveTask;
import dev.minted.bank.BankService;
import dev.minted.bank.CombatLock;
import dev.minted.bank.EconomyService;
import dev.minted.bank.EconomyStats;
import dev.minted.bank.InterestTask;
import dev.minted.bank.LoanService;
import dev.minted.bank.MoneyFormat;
import dev.minted.bank.WalletService;
import dev.minted.banknote.BanknoteListener;
import dev.minted.banknote.BanknoteManager;
import dev.minted.banknote.BanknoteParser;
import dev.minted.banknote.LostCashListener;
import dev.minted.banknote.NoteInventory;
import dev.minted.resourcepack.ResourcePackListener;
import dev.minted.wallet.WalletCommand;
import dev.minted.wallet.WalletListener;
import dev.minted.wallet.WalletManager;
import dev.minted.command.BalanceCommand;
import dev.minted.command.BankCommand;
import dev.minted.command.CommandManager;
import dev.minted.command.PayCommand;
import dev.minted.command.SellCommand;
import dev.minted.command.StatsCommand;
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
import dev.minted.shop.log.SaleLog;
import dev.minted.shop.storage.ShopDao;

import org.bukkit.Material;
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
    private static final long STATS_REFRESH_TICKS = 20L * 30L;

    private static MintedPlugin instance;

    private ServerVersion serverVersion;
    private HikariPool pool;
    private SqlStorageProvider walletStorage;
    private SqlStorageProvider bankStorage;
    private EconomyService walletEconomy;
    private EconomyService bankEconomy;
    private RequestService requestService;
    private ShopService shopService;
    private StatsDao statsDao;
    private EconomyStats stats;
    private NamesDao namesDao;
    private SaleLog saleLog;
    private LoanService loanService;
    private boolean interestEnabled;
    private double interestRate;
    private long interestTicks;
    private dev.minted.bounty.BountyService bountyService;

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
        Messages messages = Messages.load(this);
        BanknoteManager banknotes = new BanknoteManager(new BanknoteParser(format, serverVersion), denominations());
        NoteInventory noteInventory = new NoteInventory(banknotes);
        boolean walletEnabled = getConfig().getBoolean("wallet.enabled", true);
        WalletManager wallets = new WalletManager(serverVersion, noteInventory, banknotes, format, messages,
                walletEnabled, walletMaterial());
        WalletService walletService = new WalletService(physical, walletEconomy, noteInventory, wallets);

        this.statsDao = new StatsDao(pool.start(), dialect);
        this.stats = new EconomyStats(this, bankEconomy, statsDao);

        this.namesDao = new NamesDao(pool.start(), dialect);
        this.saleLog = new SaleLog(this, new SaleDao(pool, dialect));
        this.loanService = new LoanService(this, new LoansDao(pool, dialect), bankEconomy,
                getConfig().getDouble("bank.loan.max", 5000),
                getConfig().getDouble("bank.loan.fee-percent", 10),
                getConfig().getLong("bank.loan.term-minutes", 10080) * 60000,
                getConfig().getDouble("bank.loan.late-fee-percent", 2));
        this.interestEnabled = getConfig().getBoolean("bank.interest.enabled", true);
        this.interestRate = getConfig().getDouble("bank.interest.rate", 0.1);
        this.interestTicks = 20L * 60L * getConfig().getLong("bank.interest.interval-minutes", 30);

        if (getConfig().getBoolean("bounty.enabled", true)) {
            this.bountyService = new dev.minted.bounty.BountyService(this,
                    new dev.minted.backend.BountyDao(pool, dialect),
                    bankEconomy,
                    getConfig().getDouble("bounty.min", 100),
                    getConfig().getDouble("bounty.max", 1000000));
            this.bountyService.initialize();
        } else {
            getLogger().info("Bounties are disabled in config.yml.");
        }

        this.requestService = new RequestService(this, walletService, format,
                getConfig().getLong("bank.request-expiry-seconds", 60));

        CombatLock combatLock = combatLock();

        MaterialLookup materials = new MaterialLookup(serverVersion);
        Design design = new Design(new Glass(serverVersion));
        SoundFX sounds = new SoundFX(getConfig().getConfigurationSection("sounds"));

        ChatPrompt chatPrompt = new ChatPrompt(this);
        GuiContext gui = new GuiContext(walletEconomy, bankEconomy, bankService, walletService, noteInventory,
                format, chatPrompt, requestService, presets(), banknotes, messages, combatLock, design, sounds, stats,
                this, namesDao, saleLog, loanService, bountyService);

        this.shopService = new ShopService(this, new ShopDao(pool, dialect), serverVersion, materials);
        Trade trade = new Trade(walletService, bankEconomy, format, messages, stats, saleLog);
        Market market = new Market(shopService, walletService, banknotes, format, messages, saleLog);
        ShopContext shopContext = new ShopContext(shopService, trade, market, messages, format, chatPrompt,
                design, walletService, materials);

        registerListeners(chatPrompt, gui, banknotes, noteInventory, format, messages, physical, combatLock, bountyService, sounds);
        getServer().getPluginManager().registerEvents(new WalletListener(wallets), this);
        getServer().getPluginManager().registerEvents(
                new ResourcePackListener(getConfig().getConfigurationSection("resource-pack")), this);

        new CommandManager(this, gui, requestService).register();
        setExecutor("balance", new BalanceCommand(walletService, format));
        setExecutor("wallet", new WalletCommand(wallets));
        setExecutor("pay", new PayCommand(walletService, format, sounds));
        setExecutor("bank", new BankCommand(bankService, bankEconomy, banknotes, noteInventory, gui, format,
                messages, physical, combatLock));
        setExecutor("sell", new SellCommand(shopContext, banknotes, getConfig().getString("shops.global", "Spawn")));
        setExecutor("mstats", new StatsCommand(gui));
        if (bountyService != null) {
            setExecutor("bounty", new dev.minted.command.BountyCommand(gui));
        }
        registerShopCommand(shopContext);

        openStorageAsync();
    }

    private void registerListeners(ChatPrompt chatPrompt, GuiContext gui, BanknoteManager banknotes,
                                   NoteInventory noteInventory, MoneyFormat format, Messages messages,
                                   boolean physical, CombatLock combatLock, dev.minted.bounty.BountyService bounties, SoundFX sounds) {
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
        if (bounties != null) {
            dev.minted.bounty.LastHitterTracker tracker = new dev.minted.bounty.LastHitterTracker();
            events.registerEvents(tracker, this);
            events.registerEvents(new dev.minted.bounty.BountyListener(bounties, format, messages, sounds, combatLock, tracker), this);
        }
        events.registerEvents(new LostCashListener(this, banknotes, stats), this);
        events.registerEvents(new NamesListener(this, namesDao), this);
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

    // Wallet item material from config, resolved by name so an old config's
    // value or a renamed enum never crashes; LEATHER is always the final fallback.
    private Material walletMaterial() {
        String name = getConfig().getString("wallet.material", "LEATHER");
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException notAConstant) {
            Material material = Material.getMaterial(name);
            return material != null ? material : Material.LEATHER;
        }
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
                    statsDao.createTable();
                    namesDao.createTable();
                    loanService.initialize();
                    saleLog.initialize();
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
        stats.load();
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                new AccountSaveTask(walletEconomy, walletStorage), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                new AccountSaveTask(bankEconomy, bankStorage), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        final EconomyStats statsRef = stats;
        getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                statsRef.refresh();
            }
        }, STATS_REFRESH_TICKS, STATS_REFRESH_TICKS);
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                requestService.purgeExpired();
            }
        }, REQUEST_SWEEP_TICKS, REQUEST_SWEEP_TICKS);
        if (interestEnabled) {
            InterestTask interest = new InterestTask(this, bankEconomy, interestRate, loanService);
            getServer().getScheduler().runTaskTimerAsynchronously(this, interest, interestTicks, interestTicks);
            getLogger().info("Bank interest enabled: " + getConfig().getDouble("bank.interest.rate", 0.1)
                    + "% every " + getConfig().getLong("bank.interest.interval-minutes", 30) + " minutes.");
        }
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
