package eu.kennytv.maintenance.bungee;

import com.google.common.collect.Lists;
import eu.kennytv.maintenance.bungee.listener.ProxyPingListener;
import eu.kennytv.maintenance.bungee.mysql.MySQL;
import eu.kennytv.maintenance.core.Settings;
import eu.kennytv.maintenance.core.listener.IPingListener;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class SettingsBungee extends Settings {
    private final String updateQuery;
    private final String maintenanceQuery;
    private final MySQL mySQL;

    private final MaintenanceBungeeBase plugin;
    private final IPingListener pingListener;
    private Configuration config;
    private Configuration whitelist;

    SettingsBungee(final MaintenanceBungeeBase plugin) {
        this.plugin = plugin;

        final PluginManager pm = plugin.getProxy().getPluginManager();
        final ProxyPingListener listener = new ProxyPingListener(plugin, this);
        pm.registerListener(plugin, listener);
        pingListener = listener;

        reloadConfigs(createFiles());

        final Configuration mySQLSection = config.getSection("mysql");
        if (mySQLSection.getBoolean("use-mysql", false)) {
            plugin.getLogger().info("Trying to open database connection...");
            mySQL = new MySQL(mySQLSection.getString("host"),
                    mySQLSection.getInt("port"),
                    mySQLSection.getString("username"),
                    mySQLSection.getString("password"),
                    mySQLSection.getString("database"));

            // Varchar as the value regarding the possibility of saving stuff like the motd as well in future updates
            final String mySQLTable = mySQLSection.getString("table");
            mySQL.executeUpdate("CREATE TABLE IF NOT EXISTS " + mySQLTable + " (setting VARCHAR(16) PRIMARY KEY, value VARCHAR(255))");
            updateQuery = "INSERT INTO " + mySQLTable + " (setting, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = ?";
            maintenanceQuery = "SELECT * FROM " + mySQLTable + " WHERE setting = ?";
            plugin.getLogger().info("Done!");
        } else {
            mySQL = null;
            updateQuery = null;
            maintenanceQuery = null;
        }
    }

    @Override
    public void reloadConfigs() {
        reloadConfigs(false);
    }

    @Override
    public void saveConfig() {
        final File file = new File(plugin.getDataFolder(), "bungee-config.yml");
        try {
            YamlConfiguration.getProvider(YamlConfiguration.class).save(config, new OutputStreamWriter(new FileOutputStream(file), "UTF8"));
        } catch (final IOException e) {
            throw new RuntimeException("Unable to save bungee-config.yml!", e);
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
        final File file = new File(plugin.getDataFolder(), "WhitelistedPlayers.yml");
        try {
            YamlConfiguration.getProvider(YamlConfiguration.class).save(whitelist, file);
        } catch (final IOException e) {
            throw new RuntimeException("Unable to save WhitelistedPlayers.yml!", e);
        }
    }

    private void reloadConfigs(final boolean createdNewWhitelist) {
        final File file = new File(plugin.getDataFolder(), "bungee-config.yml");
        try {
            config = YamlConfiguration.getProvider(YamlConfiguration.class).load(new InputStreamReader(new FileInputStream(file), "UTF8"));
            whitelist = YamlConfiguration.getProvider(YamlConfiguration.class).load(new File(plugin.getDataFolder(), "WhitelistedPlayers.yml"));
        } catch (final IOException e) {
            throw new RuntimeException("Unable to load bungee-config.yml!", e);
        }

        if (createdNewWhitelist) {
            whitelist.set("a8179ff3-c201-4a75-bdaa-9d14aca6f83f", "KennyTV");
            saveWhitelistedPlayers();
        }

        loadSettings();
    }

    @Override
    public void loadExtraSettings() {
        whitelistedPlayers.clear();
        whitelist.getKeys().forEach(key -> whitelistedPlayers.put(UUID.fromString(key), whitelist.getString(key)));
    }

    @Override
    public void setWhitelist(final String uuid, final String s) {
        whitelist.set(uuid, s);
    }

    @Override
    public boolean createFiles() {
        if (!plugin.getDataFolder().exists())
            plugin.getDataFolder().mkdirs();

        final File file = new File(plugin.getDataFolder(), "bungee-config.yml");
        if (!file.exists()) {
            try (final InputStream in = plugin.getResourceAsStream("bungee-config.yml")) {
                Files.copy(in, file.toPath());
            } catch (final IOException e) {
                throw new RuntimeException("Unable to create bungee-config.yml file for MaintenanceBungee", e);
            }
        }

        boolean createdNewWhitelist = false;
        final File whitelistFile = new File(plugin.getDataFolder(), "WhitelistedPlayers.yml");
        if (!whitelistFile.exists()) {
            createdNewWhitelist = true;
            try {
                whitelistFile.createNewFile();
            } catch (final IOException e) {
                throw new RuntimeException("Unable to create WhitelistedPlayers.yml file for MaintenanceBungee", e);
            }
        }

        return createdNewWhitelist;
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
        return config.getIntList(path);
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
    public boolean isMaintenance() {
        if (mySQL != null) {
            mySQL.executeQuery(maintenanceQuery, rs -> {
                try {
                    if (rs.next())
                        maintenance = Boolean.parseBoolean(rs.getString("value"));
                    rs.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                }
            }, "maintenance");
        }

        return maintenance;
    }

    @Override
    public boolean reloadMaintenanceIcon() {
        return pingListener.loadIcon();
    }

    public void setMaintenanceToSQL(final boolean maintenance) {
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            final String s = String.valueOf(maintenance);
            mySQL.executeUpdate(updateQuery, "maintenance", s, s);
        });
        this.maintenance = maintenance;
    }

    public MySQL getMySQL() {
        return mySQL;
    }
}