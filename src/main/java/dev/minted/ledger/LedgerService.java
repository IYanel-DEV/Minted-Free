package dev.minted.ledger;

import dev.minted.backend.LedgerDao;
import dev.minted.backend.LedgerEntry;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

/**
 * The per-player transaction history. Money moments are recorded here by the
 * parts of the plugin that own them (pay, request, bank, shops, bounties, loans,
 * interest, admin tooling), each with a stable {@code kind} and a short detail.
 * A record is cosmetic - it never blocks the caller and a storage stumble loses
 * a row rather than a trade - and the DAO prunes old rows so the table stays
 * bounded. The player-facing history screen reads through {@link #recent}.
 */
public final class LedgerService {

    private final Plugin plugin;
    private final LedgerDao dao;

    private volatile int nextId = 1;

    public LedgerService(Plugin plugin, LedgerDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    /** Creates the table and resumes the id counter. Blocking; async caller only. */
    public void initialize() {
        dao.createTable();
        nextId = dao.maxId() + 1;
    }

    /**
     * Records one movement on a player's balance. Never blocks and never throws.
     *
     * @param uuid   the player whose balance moved
     * @param delta  amount added (or removed, when negative)
     * @param kind   stable event name for the history screen, e.g. {@code pay}
     * @param detail short human detail, e.g. "asdf1234", "Diamond sword", "from staff"
     */
    public void record(UUID uuid, double delta, String kind, String detail) {
        if (uuid == null || delta == 0) {
            return;
        }
        final int id = nextId++;
        final long ts = System.currentTimeMillis();
        final java.util.UUID player = uuid;
        final String captured = detail == null ? "" : detail;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    dao.insert(id, ts, player, delta, kind, captured);
                    if (id % 64 == 0) {
                        dao.prune(2000);
                    }
                } catch (RuntimeException ignored) {
                    // History is cosmetic; never break the money movement that caused it.
                }
            }
        });
    }

    /** The player's newest movements first. Blocking; async callers only. */
    public List<LedgerEntry> recent(UUID uuid, int limit) {
        return dao.recent(uuid, limit);
    }
}