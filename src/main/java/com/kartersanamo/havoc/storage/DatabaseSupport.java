package com.kartersanamo.havoc.storage;

import com.kartersanamo.havoc.config.HavocConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSupport {

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DatabaseSupport(JavaPlugin plugin, HavocConfig config) {
        this.plugin = plugin;
        this.enabled = config.isDatabaseEnabled();
        this.username = config.getDatabaseUsername();
        this.password = config.getDatabasePassword();
        this.jdbcUrl = "jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/"
                + config.getDatabaseName() + "?useSSL=" + config.isDatabaseUseSsl()
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public boolean initialize() {
        if (!enabled) {
            return false;
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Database enabled but MySQL driver is missing.");
            return false;
        }
        try (Connection c = openConnection();
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS havoc_salvage (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "amount INT NOT NULL" +
                    ")");
            s.execute("CREATE TABLE IF NOT EXISTS havoc_progression (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "easy_count INT NOT NULL," +
                    "medium_count INT NOT NULL," +
                    "hard_count INT NOT NULL" +
                    ")");
            s.execute("CREATE TABLE IF NOT EXISTS havoc_logs (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "epoch_ms BIGINT NOT NULL," +
                    "type VARCHAR(64) NOT NULL," +
                    "user_name VARCHAR(64) NOT NULL," +
                    "base_id VARCHAR(64) NOT NULL," +
                    "world_name VARCHAR(64) NOT NULL," +
                    "x INT NOT NULL," +
                    "y INT NOT NULL," +
                    "z INT NOT NULL," +
                    "message TEXT NOT NULL" +
                    ")");
            s.execute("CREATE TABLE IF NOT EXISTS havoc_player_stats (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "breaches_participated INT NOT NULL," +
                    "breaches_triggered INT NOT NULL," +
                    "salvage_earned INT NOT NULL," +
                    "salvage_spent INT NOT NULL," +
                    "shop_purchases INT NOT NULL" +
                    ")");
            s.execute("CREATE TABLE IF NOT EXISTS havoc_player_stats_weekly (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "breaches_participated INT NOT NULL," +
                    "breaches_triggered INT NOT NULL," +
                    "salvage_earned INT NOT NULL," +
                    "salvage_spent INT NOT NULL," +
                    "shop_purchases INT NOT NULL" +
                    ")");
            s.execute("CREATE TABLE IF NOT EXISTS havoc_player_stats_monthly (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "breaches_participated INT NOT NULL," +
                    "breaches_triggered INT NOT NULL," +
                    "salvage_earned INT NOT NULL," +
                    "salvage_spent INT NOT NULL," +
                    "shop_purchases INT NOT NULL" +
                    ")");
            s.execute("CREATE TABLE IF NOT EXISTS havoc_stats_meta (" +
                    "meta_key VARCHAR(64) PRIMARY KEY," +
                    "meta_value VARCHAR(255) NOT NULL" +
                    ")");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not initialize database schema: " + e.getMessage());
            return false;
        }
    }
}
