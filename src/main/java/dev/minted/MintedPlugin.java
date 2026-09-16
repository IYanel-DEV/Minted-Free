package dev.minted;

import dev.minted.command.CommandManager;
import dev.minted.compat.ServerVersion;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minted plugin entry point.
 *
 * <p>Only lifecycle wiring lives here. Features are attached by the
 * individual managers in {@code onEnable} so each subsystem stays
 * self-contained and testable.
 */
public final class MintedPlugin extends JavaPlugin {

    private static MintedPlugin instance;

    private ServerVersion serverVersion;
    private CommandManager commandManager;

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

        this.commandManager = new CommandManager(this);
        this.commandManager.register();

        getLogger().info("Minted " + getDescription().getVersion() + " enabled (server " + serverVersion + ").");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("Minted disabled.");
    }

    public ServerVersion getServerVersion() {
        return serverVersion;
    }
}