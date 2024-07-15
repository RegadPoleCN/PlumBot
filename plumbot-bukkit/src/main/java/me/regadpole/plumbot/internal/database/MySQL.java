package me.regadpole.plumbot.internal.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.DataBase;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQL implements Database{

    private static HikariDataSource hds; // MySQL
    @Override
    public void initialize() {
        String driver = null;
        try{
            Class.forName("com.mysql.cj.jdbc.Driver");
            driver = "com.mysql.cj.jdbc.Driver";
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
                driver = "com.mysql.jdbc.Driver";
            } catch (ClassNotFoundException ignored1) {
                PlumBot.INSTANCE.getLogger().info("无法找到数据库驱动");
            }
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setPoolName("PlumBot-MySQL");
        config.setJdbcUrl("jdbc:mysql://" + DataBase.mysql_host() +":"+ DataBase.mysql_port() + "/" + DataBase.mysql_database() + DataBase.mysql_parameters());
        config.setUsername(DataBase.mysql_user());
        config.setPassword(DataBase.mysql_password());
        if (!DataBase.pool_connectionTestQuery().isEmpty()){
            config.setConnectionTestQuery(DataBase.pool_connectionTestQuery());
        }
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
