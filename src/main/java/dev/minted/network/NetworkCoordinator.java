package dev.minted.network;

import dev.minted.bank.EconomyService;
import dev.minted.bank.NetworkHooks;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/**
 * The multi-server brain: it tells the two economy services they are on a
 * shared database, announces every committed balance change over Redis (when
 * configured), turns announcements from other servers into cache refreshes,
 * and runs the two safety tasks - a fast saver that keeps the window in which
 * another server could act on a stale value as small as possible, and a
 * periodic refresh so a server that missed an announcement heals itself.
 *
 * <p>Without Redis the guarantees still hold (changes are atomic deltas in the
 * database); announcements only make them visible sooner.
 */
public final class NetworkCoordinator implements NetworkHooks {

    private final Plugin plugin;
    private final List<EconomyService> services;
    private final String channel;
    private final String redisUri;

    private RedisSync redis;
    private BukkitTask saveTask;
    private BukkitTask refreshTask;

    public NetworkCoordinator(Plugin plugin, List<EconomyService> services) {
        this.plugin = plugin;
        this.services = services;
        this.channel = plugin.getConfig().getString("multi-server.channel", "minted:balance");
        this.redisUri = plugin.getConfig().getString("multi-server.redis", "").trim();
    }

    /** Opens the announcement channel and starts the safety tasks. */
    public void start() {
        if (!redisUri.isEmpty()) {
            try {
                redis = RedisSync.create(redisUri, channel, this::invalidate, plugin.getLogger());
                redis.start();
                plugin.getLogger().info("Multi-server: announcing balance changes on Redis channel '"
                        + channel + "'.");
            } catch (RuntimeException failure) {
                redis = null;
                plugin.getLogger().warning("Could not set up Redis announcements (" + failure.getMessage()
                        + "); continuing with database-only synchronisation.");
            }
        } else {
            plugin.getLogger().info("Multi-server: no Redis configured; balances stay safe through database"
                    + " deltas, and other servers notice within the refresh interval.");
        }

        long saveTicks = Math.max(20L, plugin.getConfig().getLong("multi-server.save-seconds", 2) * 20L);
        saveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                for (EconomyService service : services) {
                    service.flushDirty();
                }
            }
        }, saveTicks, saveTicks);

        long refreshSeconds = plugin.getConfig().getLong("multi-server.refresh-seconds", 15);
        if (refreshSeconds > 0) {
            long refreshTicks = refreshSeconds * 20L;
            refreshTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    for (EconomyService service : services) {
                        service.refreshCleanAccounts();
                    }
                }
            }, refreshTicks, refreshTicks);
        }
    }

    /** Cancels the tasks and closes the announcement channel. */
    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (redis != null) {
            redis.stop();
            redis = null;
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void announce(UUID uuid) {
        RedisSync sync = redis;
        if (sync != null) {
            sync.publish(uuid);
        }
    }

    /** An announcement from another server: pull that account back from the database. */
    private void invalidate(UUID uuid) {
        for (EconomyService service : services) {
            service.refreshAccount(uuid);
        }
    }

    /** A one-line description for /minted report and the startup log. */
    public String status() {
        if (redis == null) {
            return redisUri.isEmpty()
                    ? "shared database, database-only sync"
                    : "shared database, Redis not connected yet";
        }
        if (redis.isConnected()) {
            return "shared database, Redis connected on '" + channel + "'";
        }
        String problem = redis.lastError();
        return "shared database, Redis " + (problem == null || problem.isEmpty() ? "connecting..." : "unavailable: " + problem);
    }
}