package me.regadpole.plumbot.internal.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.DataBase;

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
        config.setJdbcUrl("jdbc:sqlite:" + new File(DataBase.sqlite_path()).getPath());
        config.setConnectionTimeout(DataBase.pool_connectionTimeout());
        config.setIdleTimeout(DataBase.pool_idleTimeout());
        config.setMaxLifetime(DataBase.pool_maxLifetime());
        config.setMaximumPoolSize(DataBase.pool_maximumPoolSize());
        config.setKeepaliveTime(DataBase.pool_keepaliveTime());
        config.setMinimumIdle(DataBase.pool_minimumIdle());
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
