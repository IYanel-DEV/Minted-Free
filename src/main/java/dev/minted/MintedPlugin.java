package dev.minted;

import dev.minted.auction.AuctionService;
import dev.minted.auction.storage.AuctionDao;
import dev.minted.auction.command.AuctionCommand;
import dev.minted.currency.CurrencyManager;
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
import dev.minted.api.MintedEconomy;
import dev.minted.api.MintedEconomyImpl;
import dev.minted.api.event.MintedBalanceChangeEvent;
import dev.minted.bank.AccountListener;
import dev.minted.bank.AccountSaveTask;
import dev.minted.bank.BalanceChangeSink;
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
import dev.minted.integration.npc.NpcManager;
import dev.minted.integration.npc.NpcStore;
import dev.minted.integration.via.ViaVersionHook;
import dev.minted.lang.LanguageCommand;
import dev.minted.lang.LanguageManager;
import dev.minted.lang.Messages;
import dev.minted.network.NetworkCoordinator;
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
import dev.minted.vip.VipListener;
import dev.minted.vip.VipService;
import dev.minted.vip.VipShopCommands;
import dev.minted.vip.VipStore;

import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
    private dev.minted.ledger.LedgerService ledgerService;
    private boolean interestEnabled;
    private double interestRate;
    private long interestTicks;
    private dev.minted.bounty.BountyService bountyService;
    private dev.minted.api.MintedEconomy economy;
    private MoneyFormat format;
    private Messages messages;
    private boolean vaultRegistered;
    private boolean vaultUnlockedRegistered;
    private boolean papiRegistered;
    private NpcManager npcManager;
    private boolean npcsActive;
    /** Why the bank-teller hook failed to start, or null. Shown in /minted report. */
    private String npcHookError;
    private ViaVersionHook viaHook;
    private AuctionService auctionService;
    private CurrencyManager currencyManager;
    private LanguageManager languageManager;
    private VipService vipService;
    private NetworkCoordinator network;

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
        setupMetrics();
        wire();

        getLogger().info("Minted " + getDescription().getVersion() + " enabled (server " + serverVersion + ").");
        // One-line snapshot so an admin can see the mode of the server they
        // just booted without opening config.yml or running /minted report.
        getLogger().info("Storage: " + getConfig().getString("database.type", "sqlite")
                + " | multi-server: " + networkStatus()
                + (vaultRegistered ? " | Vault hooked" : "")
                + (vaultUnlockedRegistered ? " | VaultUnlocked hooked" : "")
                + (papiRegistered ? " | PlaceholderAPI hooked" : ""));
    }

    /**
     * bStats (plugin id 34228): anonymous install/version counts on
     * bstats.org. Bundled and relocated into dev.minted.libs.bstats by the
     * shade plugin; server owners can opt out in their bStats config. Chart
     * callbacks only read config values, so they never risk an async race.
     */
    private void setupMetrics() {
        try {
            Metrics metrics = new Metrics(this, 34228);
            metrics.addCustomChart(new SimplePie("economyMode",
                    () -> getConfig().getBoolean("economy.physical", true) ? "physical" : "digital"));
            metrics.addCustomChart(new SimplePie("storageBackend",
                    () -> getConfig().getString("database.type", "sqlite")));
            metrics.addCustomChart(new SimplePie("bankTellers",
                    () -> getConfig().getBoolean("integrations.npcs.enabled", true) ? "enabled" : "disabled"));
        } catch (Throwable failure) {
            getLogger().warning("Could not start bStats metrics: " + failure);
        }
    }

    @Override
    public void onDisable() {
        if (network != null) {
            // Stop the tasks first; the shutdown flush below then runs as a
            // normal, final delta commit.
            network.stop();
        }
        if (walletEconomy != null && walletEconomy.isReady()) {
            walletEconomy.saveAllBlocking();
        }
        if (bankEconomy != null && bankEconomy.isReady()) {
            bankEconomy.saveAllBlocking();
        }
        if (shopService != null) {
            shopService.shutdown();
        }
        if (vipService != null) {
            vipService.shutdown();
        }
        if (npcManager != null) {
            npcManager.shutdown();
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
        double withdrawPercent = getConfig().getDouble("bank.fee.withdraw-percent", 0);
        double transferFeePercent = getConfig().getDouble("bank.fee.transfer-percent", 0);
        boolean physical = getConfig().getBoolean("economy.physical", true);
        this.walletEconomy = new EconomyService(this, walletStorage, starting, max);
        this.bankEconomy = new EconomyService(this, bankStorage, 0, max);
        if (getConfig().getBoolean("multi-server.enabled", false)) {
            // Several servers, one database: both economies switch to atomic
            // deltas, re-read players on join and announce committed changes.
            this.network = new NetworkCoordinator(this, Arrays.asList(walletEconomy, bankEconomy));
            walletEconomy.setNetworkHooks(network);
            bankEconomy.setNetworkHooks(network);
        }
        boolean walletPrimary = "wallet".equalsIgnoreCase(
                getConfig().getString("integrations.primary-balance", "bank"));
        this.economy = new dev.minted.api.MintedEconomyImpl(walletEconomy, bankEconomy,
                MoneyFormat.from(getConfig()),
                walletPrimary ? dev.minted.api.MintedEconomyImpl.Primary.WALLET
                        : dev.minted.api.MintedEconomyImpl.Primary.BANK);
        BankService bankService = new BankService(walletEconomy, bankEconomy, max);

        MoneyFormat format = MoneyFormat.from(getConfig());
        this.format = format;
        wireIntegrations(format);
        
        // Language manager (must be created early for other systems to use)
        this.languageManager = new LanguageManager(this, pool, dialect);
        this.languageManager.initialize();

        // Messages is a per-player view over the language manager, so /language
        // changes take effect everywhere immediately - not just at startup.
        this.messages = Messages.create(languageManager);
        Messages messages = this.messages;
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
        this.ledgerService = new dev.minted.ledger.LedgerService(this,
                new dev.minted.backend.LedgerDao(pool, dialect));
        this.loanService = new LoanService(this, new LoansDao(pool, dialect), bankEconomy,
                getConfig().getDouble("bank.loan.max", 5000),
                getConfig().getDouble("bank.loan.fee-percent", 10),
                getConfig().getLong("bank.loan.term-minutes", 10080) * 60000,
                getConfig().getDouble("bank.loan.late-fee-percent", 2),
                ledgerService);
        this.interestEnabled = getConfig().getBoolean("bank.interest.enabled", true);
        this.interestRate = getConfig().getDouble("bank.interest.rate", 0.1);
        this.interestTicks = 20L * 60L * getConfig().getLong("bank.interest.interval-minutes", 30);

        if (getConfig().getBoolean("bounty.enabled", true)) {
            this.bountyService = new dev.minted.bounty.BountyService(this,
                    new dev.minted.backend.BountyDao(pool, dialect),
                    bankEconomy,
                    getConfig().getDouble("bounty.min", 100),
                    getConfig().getDouble("bounty.max", 1000000),
                    getConfig().getDouble("bounty.max-open", 2000000),
                    getConfig().getLong("bounty.expiry-days", 0) * 24L * 60L * 60L * 1000L,
                    ledgerService);
            this.bountyService.initialize();
        } else {
            getLogger().info("Bounties are disabled in config.yml.");
        }

        this.requestService = new RequestService(this, walletService, format,
                getConfig().getLong("bank.request-expiry-seconds", 60), ledgerService);

        CombatLock combatLock = combatLock();

        MaterialLookup materials = new MaterialLookup(serverVersion);
        getLogger().info("Material probe: " + MaterialLookup.probe());
        this.viaHook = new ViaVersionHook(this);
        if (viaHook.isPresent()) {
            getLogger().info("Hooked ViaVersion: client-aware catalog items are active.");
        }
        Design design = new Design(new Glass(serverVersion));
        SoundFX sounds = new SoundFX(getConfig().getConfigurationSection("sounds"));

        ChatPrompt chatPrompt = new ChatPrompt(this);
        GuiContext gui = new GuiContext(walletEconomy, bankEconomy, bankService, walletService, noteInventory,
                format, chatPrompt, requestService, presets(), banknotes, messages, combatLock, design, sounds, stats,
                this, namesDao, saleLog, loanService, bountyService, ledgerService, withdrawPercent);

        this.shopService = new ShopService(this, new ShopDao(pool, dialect), serverVersion, materials);
        getServer().getServicesManager().register(dev.minted.api.MintedItems.class,
                new dev.minted.api.MintedItems(materials, serverVersion), this, ServicePriority.Normal);
        Trade trade = new Trade(walletService, bankEconomy, format, messages, stats, saleLog, ledgerService);
        Market market = new Market(shopService, walletService, banknotes, format, messages, saleLog);
        ShopContext shopContext = new ShopContext(shopService, trade, market, messages, format, chatPrompt,
                design, walletService, materials);

        // VIPs: the admin list behind /minted vip and the dashboard, plus the
        // automatic /<username> shop commands derived from it. The shop hooks
        // make those commands follow shop creation, deletion and reloads.
        this.vipService = new VipService(this, new VipStore(getDataFolder()), namesDao, messages, shopContext);
        vipService.setAliases(new VipShopCommands(this, shopContext, vipService));
        shopService.setPlayerShopHooks(vipService);
        getServer().getPluginManager().registerEvents(new VipListener(vipService), this);

        // Auction house
        AuctionDao auctionDao = new AuctionDao(pool, dialect);
        double listingFeePercent = getConfig().getDouble("auction.listing-fee-percent", 1.0);
        double minStartPrice = getConfig().getDouble("auction.min-start-price", 1.0);
        int maxDurationHours = getConfig().getInt("auction.max-duration-hours", 168);
        int minDurationMinutes = getConfig().getInt("auction.min-duration-minutes", 10);
        int maxItemsPerPlayer = getConfig().getInt("auction.max-per-player", 10);
        this.auctionService = new AuctionService(auctionDao, walletService, walletEconomy, format, messages, ledgerService,
                listingFeePercent, minStartPrice, maxDurationHours, minDurationMinutes, maxItemsPerPlayer);

        // Multi-currency
        this.currencyManager = new CurrencyManager(pool, dialect, messages);
        if (getConfig().getBoolean("multi-currency.enabled", false)) {
            this.currencyManager.initialize();
        }

        registerListeners(chatPrompt, gui, banknotes, noteInventory, format, messages, physical, combatLock, bountyService, sounds);
        getServer().getPluginManager().registerEvents(new WalletListener(wallets), this);
        getServer().getPluginManager().registerEvents(
                new ResourcePackListener(getConfig().getConfigurationSection("resource-pack")), this);

        // The teller hook must run before the command manager is built: /minted npc
        // holds the manager reference for its whole lifetime, so building it first
        // would capture the pre-hook null and always report "not available".
        hookNpcs(gui);

        new CommandManager(this, gui, requestService, npcManager).register();
        setExecutor("balance", new BalanceCommand(walletService, format));
        setExecutor("wallet", new WalletCommand(wallets));
        setExecutor("pay", new PayCommand(this, walletService, bankEconomy, namesDao, format, sounds, ledgerService,
                transferFeePercent, stats));
        setExecutor("bank", new BankCommand(bankService, bankEconomy, banknotes, noteInventory, gui, format,
                messages, physical, combatLock, ledgerService, withdrawPercent, stats));
        setExecutor("sell", new SellCommand(shopContext, banknotes, getConfig().getString("shops.global", "Spawn")));
        setExecutor("mstats", new StatsCommand(gui));
        setExecutor("mhistory", new dev.minted.command.HistoryCommand(gui));
        setExecutor("eco", new dev.minted.command.EcoCommand(this, economy, format, ledgerService,
                new dev.minted.command.BalanceTransfer(this, economy, ledgerService)));
        if (bountyService != null) {
            setExecutor("bounty", new dev.minted.command.BountyCommand(gui));
        }
        setExecutor("ah", new AuctionCommand(shopContext, auctionService, messages));
        setExecutor("language", new LanguageCommand(languageManager));
        registerShopCommand(shopContext);
        setExecutor("pshop", new dev.minted.shop.command.PlayerShopCommand(
                shopContext, getConfig().getInt("shops.max-player-shops", 1)));
        getCommand("pshop").setTabCompleter(new dev.minted.shop.command.PlayerShopTabCompleter(shopContext));
        hookVault();
        hookVaultUnlocked();
        hookPapi();
        hookEssentials();

        openStorageAsync();
    }

    /**
     * Enables the self-built bank tellers when ProtocolLib is installed and
     * {@code integrations.npcs.enabled} is on. All ProtocolLib references live
     * behind this guard: without the plugin nothing in the npc package is ever
     * loaded, and the tellers degrade silently to "not available".
     */
    private void hookNpcs(GuiContext gui) {
        if (!getConfig().getBoolean("integrations.npcs.enabled", true)) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().info("ProtocolLib is not installed; bank tellers are disabled.");
            return;
        }
        try {
            this.npcManager = new NpcManager(this, serverVersion, gui,
                    new NpcStore(getDataFolder()),
                    getConfig().getLong("integrations.npcs.tab-hide-seconds", 5),
                    getConfig().getString("integrations.npcs.name", "&eBanker"),
                    getConfig().getString("integrations.npcs.skin-value", ""),
                    getConfig().getString("integrations.npcs.skin-signature", ""),
                    getConfig().getString("integrations.npcs.skin-player", ""));
            this.npcManager.initialize();
            getServer().getPluginManager().registerEvents(this.npcManager, this);
            this.npcsActive = true;
            this.npcHookError = null;
            getLogger().info("Hooked ProtocolLib: bank tellers are available (/minted npc).");
        } catch (Throwable failure) {
            this.npcManager = null;
            this.npcsActive = false;
            this.npcHookError = failure.getClass().getSimpleName() + ": "
                    + String.valueOf(failure.getMessage());
            getLogger().warning("Could not hook ProtocolLib for bank tellers ("
                    + npcHookError + "). This is usually a ProtocolLib build that does not match"
                    + " your server version; install a build built for your server.");
        }
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

    /**
     * Publishes the public API and connects the balance-change events. The
     * event sink is attached before any account is adopted, so it sees every
     * change from the first join onwards. Nothing here depends on a third-party
     * plugin being present: the service is always registered and the events
     * always fire, which is what lets other plugins hook Minted.
     */
    private void wireIntegrations(MoneyFormat format) {
        if (getConfig().getBoolean("integrations.events", true)) {
            this.walletEconomy.setChangeSink(sink(MintedBalanceChangeEvent.Account.WALLET));
            this.bankEconomy.setChangeSink(sink(MintedBalanceChangeEvent.Account.BANK));
        }

        getServer().getServicesManager().register(MintedEconomy.class,
                this.economy,
                this, ServicePriority.Normal);
    }

    private BalanceChangeSink sink(final MintedBalanceChangeEvent.Account account) {
        return new BalanceChangeSink() {
            @Override
            public void changed(UUID uuid, double oldBalance, double newBalance) {
                postBalanceEvent(uuid, account, oldBalance, newBalance);
            }
        };
    }

    // Bukkit events must fire on the main thread; interest and other async
    // savers can trigger a balance change off it, so bounce those back.
    private void postBalanceEvent(final UUID uuid, final MintedBalanceChangeEvent.Account account,
                                  final double oldBalance, final double newBalance) {
        final MintedBalanceChangeEvent event =
                new MintedBalanceChangeEvent(uuid, account, oldBalance, newBalance);
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            getServer().getPluginManager().callEvent(event);
        } else {
            getServer().getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    getServer().getPluginManager().callEvent(event);
                }
            });
        }
    }

    /**
     * Essentials runs its own economy by default. When both are live the two
     * never disagree for Vault callers - Minted registers at the higher
     * priority - but the duplicated balances confuse players, so this warns
     * once at startup and points at the fix. Discovered through the live Vault
     * resolution, never through a hard Essentials dependency.
     */
    private void hookEssentials() {
        if (getServer().getPluginManager().getPlugin("Essentials") == null) {
            return;
        }
        boolean mintedHolding = isMintedTheEconomy();
        boolean essentialsLiving = essentialsEconomyActive();
        if (!getConfig().getBoolean("integrations.essentials.warn", true)) {
            return;
        }
        if (mintedHolding && essentialsLiving) {
            getLogger().info("EssentialsX is running its own economy next to Minted. Vault callers resolve"
                    + " to Minted, but /bal and /pay-style balances shown by Essentials use that second"
                    + " economy. To run Essentials entirely on Minted, disable Essentials' built-in"
                    + " economy (economy: disabled in its config.yml).");
        } else if (essentialsLiving && !mintedHolding) {
            getLogger().info("EssentialsX has the Vault economy. Set integrations.primary-balance in Minted's"
                    + " config and ensure Minted is the Vault priority if you want Essentials to use Minted.");
        }
    }

    public dev.minted.integration.IntegrationReport integrationReport() {
        boolean vault = getServer().getPluginManager().getPlugin("Vault") != null;
        boolean vaultUnlocked = dev.minted.integration.vaultunlocked.VaultUnlockedHook.apiPresent();
        boolean papi = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        boolean essentials = getServer().getPluginManager().getPlugin("Essentials") != null;
        boolean protocolLib = getServer().getPluginManager().getPlugin("ProtocolLib") != null;
        boolean via = viaHook != null && viaHook.isPresent();
        boolean ready = walletEconomy != null && walletEconomy.isReady()
                && bankEconomy != null && bankEconomy.isReady();
        return new dev.minted.integration.IntegrationReport(ready, vault, vaultRegistered,
                vaultUnlocked, vaultUnlockedRegistered,
                papi, papiRegistered, essentials, essentials && essentialsEconomyActive(),
                protocolLib, npcsActive, via, npcHookError,
                getConfig().getString("integrations.primary-balance", "bank"));
    }

    /** The optional ViaVersion hook; never null, degrades when the plugin is absent. */
    public ViaVersionHook viaHook() {
        return viaHook;
    }

    /** @return true when Minted itself is the active Vault economy provider. */
    private boolean isMintedTheEconomy() {
        Object provider = vaultProvider();
        return provider != null && provider.getClass().getName().contains("VaultEconomy");
    }

    /** @return true when Essentials' own economy service is registered with Vault. */
    private boolean essentialsEconomyActive() {
        List<?> registrations = economyRegistrations();
        if (registrations == null) {
            return false;
        }
        for (Object registration : registrations) {
            try {
                Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
                if (provider.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("essential")) {
                    return true;
                }
            } catch (Throwable ignored) {
                // one broken registration must never take Minted down
            }
        }
        return false;
    }

    // Reflection through Vault's Economy interface so this never becomes a hard
    // dependency: a server without Vault simply has no registration to find.
    private Object vaultProvider() {
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = getServer().getServicesManager().getRegistration(economy);
            return registration == null ? null
                    : registration.getClass().getMethod("getProvider").invoke(registration);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private List<?> economyRegistrations() {
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy");
            return (List<?>) getServer().getServicesManager().getClass()
                    .getMethod("getRegistrations", Class.class)
                    .invoke(getServer().getServicesManager(), economy);
        } catch (Throwable ignored) {
            return null;
        }
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
                    ledgerService.initialize();
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
        if (network != null) {
            network.start();
        }
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
            InterestTask interest = new InterestTask(this, bankEconomy, interestRate, loanService, ledgerService);
            getServer().getScheduler().runTaskTimerAsynchronously(this, interest, interestTicks, interestTicks);
            getLogger().info("Bank interest enabled: " + getConfig().getDouble("bank.interest.rate", 0.1)
                    + "% every " + getConfig().getLong("bank.interest.interval-minutes", 30) + " minutes.");
        }
        if (bountyService != null && bountyService.expiryMillis() > 0) {
            dev.minted.bounty.BountyExpiryTask expiry = new dev.minted.bounty.BountyExpiryTask(
                    this, bountyService, format, messages);
            getServer().getScheduler().runTaskTimer(this, expiry, REQUEST_SWEEP_TICKS, REQUEST_SWEEP_TICKS);
        }
        // Auction house cleanup - check for expired auctions every minute
        getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                if (auctionService != null) {
                    auctionService.cleanupExpired();
                }
            }
        }, 20L * 60L, 20L * 60L);
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

    /**
     * Registers Minted with Vault when it is installed and enabled in config.
     * The Vault types live in a separate class so they are only resolved after
     * this guard passes; any failure is logged and ignored rather than taking
     * the plugin down.
     */
    private void hookVault() {
        if (!getConfig().getBoolean("integrations.vault.register", true)) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        try {
            dev.minted.integration.vault.VaultHook.register(this, dev.minted.api.MintedAPI.economy());
            this.vaultRegistered = true;
        } catch (Throwable failure) {
            getLogger().warning("Could not hook Vault (" + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage() + "); continuing without it.");
        }
    }

    /**
     * Registers Minted with VaultUnlocked when its API classes are on the
     * server and the config allows it. The guard resolves the classes through
     * this plugin's own classloader (which can see other plugins' jars), so
     * nothing VaultUnlocked-specific is ever loaded when it is absent - same
     * degradation contract as {@link #hookVault()}.
     */
    private void hookVaultUnlocked() {
        if (!getConfig().getBoolean("integrations.vaultunlocked.register", true)) {
            return;
        }
        if (!dev.minted.integration.vaultunlocked.VaultUnlockedHook.apiPresent()) {
            return;
        }
        try {
            dev.minted.integration.vaultunlocked.VaultUnlockedHook.register(this,
                    dev.minted.api.MintedAPI.economy());
            this.vaultUnlockedRegistered = true;
        } catch (Throwable failure) {
            getLogger().warning("Could not hook VaultUnlocked (" + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage() + "); continuing without it.");
        }
    }

    /**
     * Registers the {@code %minted_*%} placeholders when PlaceholderAPI is
     * installed and enabled in config. Like the Vault hook, all PlaceholderAPI
     * references live in a separate class that is only loaded once this guard
     * passes; a failure is logged and ignored.
     */
    private void hookPapi() {
        if (!getConfig().getBoolean("integrations.placeholders.register", true)) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            dev.minted.integration.placeholder.MintedExpansion expansion =
                    new dev.minted.integration.placeholder.MintedExpansion(
                            dev.minted.api.MintedAPI.economy(), stats, getDescription().getVersion());
            expansion.register();
            this.papiRegistered = true;
            getLogger().info("Hooked PlaceholderAPI: %minted_*% placeholders are available.");
        } catch (Throwable failure) {
            getLogger().warning("Could not hook PlaceholderAPI (" + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage() + "); continuing without it.");
        }
    }

    public ServerVersion getServerVersion() {
        return serverVersion;
    }

    public EconomyService getEconomyService() {
        return walletEconomy;
    }

    public AuctionService getAuctionService() {
        return auctionService;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    /** The VIP list behind /minted vip and the dashboard's VIP page. */
    public VipService getVipService() {
        return vipService;
    }

    /** "off" on a single server, otherwise a short multi-server status line. */
    public String networkStatus() {
        return network == null ? "off" : network.status();
    }
}
