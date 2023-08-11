package me.regadpole.plumbot.internal.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Database {
    void initialize() throws ClassNotFoundException;

    void close() throws SQLException;

    Connection getConnection() throws SQLException;
}