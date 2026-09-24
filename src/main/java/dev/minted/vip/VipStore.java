package dev.minted.vip;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists the VIP list in {@code vips.yml} inside the plugin folder. The
 * {@code entries} section holds one section per list VIP keyed by uuid, so
 * reordering or a rename never loses an entry; the {@code permission} section
 * keeps the last-seen names of players who count through the
 * {@code minted.vip} permission (offline permissions are not readable, so the
 * last observed state is what a restart resumes from). The file is tiny, so
 * every change is written straight away - the same contract the bank-teller
 * store keeps.
 */
public final class VipStore {

    private final File file;

    public VipStore(File dataFolder) {
        this.file = new File(dataFolder, "vips.yml");
    }

    public List<VipEntry> load() {
        List<VipEntry> entries = new ArrayList<VipEntry>();
        if (!file.exists()) {
            return entries;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("entries");
        if (root == null) {
            return entries;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            VipEntry entry = VipEntry.stored(section);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** The players who last counted through the minted.vip permission: uuid to name. */
    public Map<UUID, String> loadPermissions() {
        Map<UUID, String> holders = new LinkedHashMap<UUID, String>();
        if (!file.exists()) {
            return holders;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("permission");
        if (section == null) {
            return holders;
        }
        for (String key : section.getKeys(false)) {
            String name = section.getString(key);
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                holders.put(UUID.fromString(key), name);
            } catch (IllegalArgumentException badId) {
                // Skip a corrupted id; the player is re-recorded on join.
            }
        }
        return holders;
    }

    public void save(Collection<VipEntry> entries, Map<UUID, String> permission) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("entries");
        for (VipEntry entry : entries) {
            entry.storeTo(root.createSection(entry.getUuid().toString()));
        }
        ConfigurationSection holders = yaml.createSection("permission");
        for (Map.Entry<UUID, String> holder : permission.entrySet()) {
            holders.set(holder.getKey().toString(), holder.getValue());
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            throw new IllegalStateException("Could not save " + file.getName() + ": " + e.getMessage(), e);
        }
    }
}