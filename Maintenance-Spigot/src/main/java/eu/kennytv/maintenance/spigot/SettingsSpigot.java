package eu.kennytv.maintenance.spigot;

import com.google.common.collect.Lists;
import eu.kennytv.maintenance.core.Settings;
import eu.kennytv.maintenance.core.listener.IPingListener;
import eu.kennytv.maintenance.spigot.listener.PacketListener;
import eu.kennytv.maintenance.spigot.listener.ServerListPingListener;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public final class SettingsSpigot extends Settings {
    private final MaintenanceSpigotBase plugin;
    private final IPingListener pingListener;
    private FileConfiguration config;
    private FileConfiguration whitelist;

    SettingsSpigot(final MaintenanceSpigotBase plugin) {
        this.plugin = plugin;

        final PluginManager pm = plugin.getServer().getPluginManager();
        if (pm.getPlugin("ProtocolLib") != null) {
            pingListener = new PacketListener(plugin, this);
        } else {
            final ServerListPingListener listener = new ServerListPingListener(plugin, this);
            pm.registerEvents(listener, plugin);
            pingListener = listener;
        }

        createFiles();
        reloadConfigs();
    }

    @Override
    public void saveConfig() {
        try {
            config.save(new File(plugin.getDataFolder(), "spigot-config.yml"));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateConfig() {
        if (!config.contains("pingmessage")) return;
        config.set("pingmessages", Lists.newArrayList(getConfigString("pingmessage")));
        config.set("pingmessage", null);
        saveConfig();
    }

    @Override
    public void saveWhitelistedPlayers() {
        try {
            whitelist.save(new File(plugin.getDataFolder(), "WhitelistedPlayers.yml"));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reloadConfigs() {
        final File file = new File(plugin.getDataFolder(), "spigot-config.yml");
        try {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(file), "UTF8"));
            whitelist = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "WhitelistedPlayers.yml"));
        } catch (final IOException e) {
            throw new RuntimeException("Unable to load spigot-config.yml!", e);
        }

        loadSettings();
    }

    @Override
    public void loadExtraSettings() {
        whitelistedPlayers.clear();
        whitelist.getKeys(false).forEach(key -> whitelistedPlayers.put(UUID.fromString(key), whitelist.getString(key)));
    }

    @Override
    public void setWhitelist(final String uuid, final String s) {
        whitelist.set(uuid, s);
    }

    @Override
    public boolean createFiles() {
        if (!plugin.getDataFolder().exists())
            plugin.getDataFolder().mkdirs();

        final File file = new File(plugin.getDataFolder(), "spigot-config.yml");
        if (!file.exists()) {
            try (final InputStream in = plugin.getResource("spigot-config.yml")) {
                Files.copy(in, file.toPath());
            } catch (final IOException e) {
                throw new RuntimeException("Unable to create spigot-config.yml file for Maintenance!", e);
            }
        }

        final File whitelistFile = new File(plugin.getDataFolder(), "WhitelistedPlayers.yml");
        if (!whitelistFile.exists()) {
            try (final InputStream in = plugin.getResource("WhitelistedPlayers.yml")) {
                Files.copy(in, whitelistFile.toPath());
            } catch (final IOException e) {
                throw new RuntimeException("Unable to create WhitelistedPlayers.yml file for Maintenance!", e);
            }
        }
        return true;
    }

    @Override
    public String getConfigString(final String path) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(path));
    }

    @Override
    public boolean getConfigBoolean(final String path) {
        return config.getBoolean(path);
    }

    @Override
    public List<Integer> getConfigIntList(final String path) {
        return config.getIntegerList(path);
    }

    @Override
    public List<String> getConfigList(final String path) {
        return config.getStringList(path);
    }

    @Override
    public void setToConfig(final String path, final Object var) {
        config.set(path, var);
    }

    @Override
    public String getColoredString(final String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public boolean reloadMaintenanceIcon() {
        return pingListener.loadIcon();
    }
}