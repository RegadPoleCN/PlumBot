package me.regadpole.plumbot.internal.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.internal.DbConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class SQLite implements Database{

    private static HikariDataSource hds; // MySQL

    @Override
    public void initialize() {

        String driver = null;
        try{
            Class.forName("org.sqlite.JDBC");
            driver = "org.sqlite.JDBC";
        } catch (ClassNotFoundException ignored) {
            PlumBot.INSTANCE.getLogger().info("无法找到数据库驱动");
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setPoolName("PlumBot-SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + new File(DbConfig.settings.sqlite.path).getPath());
        config.setConnectionTimeout(DbConfig.settings.pool.connectionTimeout);
        config.setIdleTimeout(DbConfig.settings.pool.idleTimeout);
        config.setMaxLifetime(DbConfig.settings.pool.maxLifetime);
        config.setMaximumPoolSize(DbConfig.settings.pool.maximumPoolSize);
        config.setKeepaliveTime(DbConfig.settings.pool.keepaliveTime);
        config.setMinimumIdle(DbConfig.settings.pool.minimumIdle);
        config.addDataSourceProperty("cachePrepStmts", "true" );
        config.addDataSourceProperty("prepStmtCacheSize", "250" );
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048" );

        hds = new HikariDataSource(config);
    }

    @Override
    public void close() {
        hds.close();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return hds.getConnection();
    }
}
