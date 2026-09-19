package dev.minted.integration.npc;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the placed bank tellers in {@code npcs.yml}. One section per
 * teller, keyed {@code npc-0}, {@code npc-1}, ...; the stable identity lives
 * inside the section (the {@code uuid}), so reordering never matters.
 */
public final class NpcStore {

    private final File file;

    public NpcStore(File dataFolder) {
        this.file = new File(dataFolder, "npcs.yml");
    }

    public List<BankNpc> load() {
        List<BankNpc> npcs = new ArrayList<BankNpc>();
        if (!file.exists()) {
            return npcs;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            BankNpc npc = BankNpc.stored(section);
            if (npc != null) {
                npcs.add(npc);
            }
        }
        return npcs;
    }

    public void save(List<BankNpc> npcs) {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (BankNpc npc : npcs) {
            npc.storeTo(yaml.createSection("npc-" + index++));
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            throw new IllegalStateException("Could not save " + file.getName() + ": " + e.getMessage(), e);
        }
    }
}