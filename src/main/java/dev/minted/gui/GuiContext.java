package dev.minted.gui;

import dev.minted.backend.NamesDao;
import dev.minted.bank.BankService;
import dev.minted.bounty.BountyService;
import dev.minted.bank.CombatLock;
import dev.minted.bank.EconomyService;
import dev.minted.bank.EconomyStats;
import dev.minted.bank.LoanService;
import dev.minted.bank.MoneyFormat;
import dev.minted.bank.WalletService;
import dev.minted.banknote.BanknoteManager;
import dev.minted.banknote.NoteInventory;
import dev.minted.gui.theme.Design;
import dev.minted.lang.Messages;
import dev.minted.request.RequestService;
import dev.minted.shop.log.SaleLog;
import dev.minted.sound.SoundFX;

import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * The shared dependencies every menu needs, passed as one value so menu
 * constructors stay about their own subject (a target player, an amount) rather
 * than re-listing services.
 */
public final class GuiContext {

    private final EconomyService wallet;
    private final EconomyService bankEconomy;
    private final BankService bank;
    private final WalletService walletService;
    private final NoteInventory notes;
    private final MoneyFormat format;
    private final ChatPrompt prompt;
    private final RequestService requests;
    private final double[] presets;
    private final BanknoteManager banknotes;
    private final Messages messages;
    private final CombatLock combatLock;
    private final Design design;
    private final SoundFX sounds;
    private final EconomyStats stats;
    private final Plugin plugin;
    private final NamesDao names;
    private final SaleLog sales;
    private final LoanService loans;
    private final BountyService bounties;

    public GuiContext(EconomyService wallet, EconomyService bankEconomy, BankService bank, WalletService walletService,
                      NoteInventory notes, MoneyFormat format, ChatPrompt prompt, RequestService requests,
                      double[] presets, BanknoteManager banknotes, Messages messages, CombatLock combatLock,
                      Design design, SoundFX sounds, EconomyStats stats, Plugin plugin,
                      NamesDao names, SaleLog sales, LoanService loans, BountyService bounties) {
        this.wallet = wallet;
        this.bankEconomy = bankEconomy;
        this.bank = bank;
        this.walletService = walletService;
        this.notes = notes;
        this.format = format;
        this.prompt = prompt;
        this.requests = requests;
        this.presets = presets;
        this.banknotes = banknotes;
        this.messages = messages;
        this.combatLock = combatLock;
        this.design = design;
        this.sounds = sounds;
        this.stats = stats;
        this.plugin = plugin;
        this.names = names;
        this.sales = sales;
        this.loans = loans;
        this.bounties = bounties;
    }

    public Design design() {
        return design;
    }

    public SoundFX sounds() {
        return sounds;
    }

    /** True once storage is open and this player's accounts are cached. */
    public boolean ready(UUID uuid) {
        return bank.isReady() && bank.isLoaded(uuid);
    }

    EconomyService wallet() {
        return wallet;
    }

    EconomyService bankEconomy() {
        return bankEconomy;
    }

    BankService bank() {
        return bank;
    }

    public WalletService walletService() {
        return walletService;
    }

    NoteInventory notes() {
        return notes;
    }

    BanknoteManager banknotes() {
        return banknotes;
    }

    public Messages messages() {
        return messages;
    }

    CombatLock combatLock() {
        return combatLock;
    }

    MoneyFormat format() {
        return format;
    }

    ChatPrompt prompt() {
        return prompt;
    }

    RequestService requests() {
        return requests;
    }

    double[] presets() {
        return presets;
    }

    EconomyStats stats() {
        return stats;
    }

    Plugin plugin() {
        return plugin;
    }

    NamesDao names() {
        return names;
    }

    SaleLog sales() {
        return sales;
    }

    LoanService loans() {
        return loans;
    }

    public BountyService bounties() {
        return bounties;
    }
}
