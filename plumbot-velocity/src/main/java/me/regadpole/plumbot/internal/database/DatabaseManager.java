package me.regadpole.plumbot.internal.database;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    public static void addBind(String id, long qq, String mode, Database db) {
        String createTable = "CREATE TABLE IF NOT EXISTS whitelist (id TINYTEXT NOT NULL, qq long NOT NULL);";
        String selectid = "SELECT * FROM whitelist WHERE id='" + id + "' LIMIT 1";
        String selectqq = "SELECT * FROM whitelist WHERE qq=" + qq + " LIMIT 1";
        String updateid = "UPDATE whitelist SET id='" + id + "' WHERE qq=" + qq + ";";
        String updateqq = "UPDATE whitelist SET qq=" + qq + " WHERE id='" + id + "';";
        String insert = "insert into whitelist values('" + id + "', " + qq + ");";

        try {
            Connection connection = db.getConnection();
            switch (mode) {
                case "sqlite":
                default: {
                    Statement statement = connection.createStatement();
                    Statement statement1 = connection.createStatement();
                    statement.executeUpdate(createTable);

                    ResultSet resultSetid = statement.executeQuery(selectid);
                    ResultSet resultSetqq = statement1.executeQuery(selectqq);

                    if (!resultSetid.isBeforeFirst() && resultSetqq.isBeforeFirst()) {
                        statement.executeUpdate(updateid);
                    } else if (resultSetid.isBeforeFirst() && !resultSetqq.isBeforeFirst()) {
                        statement.executeUpdate(updateqq);
                    } else if (!resultSetid.isBeforeFirst() && !resultSetqq.isBeforeFirst()) {
                        statement.executeUpdate(insert);
                    }

                    resultSetid.close();
                    resultSetqq.close();
                    statement.close();
                    statement1.close();
                    connection.close();

                    break;
                }
                case "mysql": {

                    connection.prepareStatement(createTable).executeUpdate();

                    ResultSet resultSetid = connection.prepareStatement(selectid).executeQuery();
                    ResultSet resultSetqq = connection.prepareStatement(selectqq).executeQuery();

                    if (!resultSetid.isBeforeFirst() && resultSetqq.isBeforeFirst()) {
                        connection.prepareStatement(updateid).executeUpdate();
                    } else if (resultSetid.isBeforeFirst() && !resultSetqq.isBeforeFirst()) {
                        connection.prepareStatement(updateqq).executeUpdate();
                    } else if (!resultSetid.isBeforeFirst() && !resultSetqq.isBeforeFirst()) {
                        connection.prepareStatement(insert).executeUpdate();
                    }

                    resultSetid.close();
                    resultSetqq.close();
                    connection.close();
                    break;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public static void removeBindid(String id,String mode, Database db) {
        String createTable = "CREATE TABLE IF NOT EXISTS whitelist (id TINYTEXT NOT NULL, qq long NOT NULL);";
        String select = "SELECT * FROM whitelist WHERE id='" + id + "' LIMIT 1;";
        String delete = "DELETE FROM whitelist WHERE id='" + id + "';";

        try {
            Connection connection = db.getConnection();
            switch (mode) {
                case "sqlite":
                default: {
                    Statement statement = connection.createStatement();
                    statement.executeUpdate(createTable);

                    ResultSet resultSet = statement.executeQuery(select);

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                        statement.executeUpdate(delete);
                    }
                    resultSet.close();
                    statement.close();
                    connection.close();

                    break;
                }
                case "mysql": {
                    connection.prepareStatement(createTable).executeUpdate();

                    ResultSet resultSet = connection.prepareStatement(select).executeQuery();

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                        connection.prepareStatement(delete).executeUpdate();
                    }
                    resultSet.close();
                    connection.close();
                    break;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void removeBind(long qq,String mode, Database db) {
        String createTable = "CREATE TABLE IF NOT EXISTS whitelist (id TINYTEXT NOT NULL, qq long NOT NULL);";
        String select = "SELECT * FROM whitelist WHERE qq=" + qq + " LIMIT 1;";
        String delete = "DELETE FROM whitelist WHERE qq=" + qq+";";

        try {
            Connection connection = db.getConnection();
            switch (mode){
                case "sqlite":
                default: {
                    Statement statement = connection.createStatement();
                    statement.executeUpdate(createTable);

                    // 如果没有找到记录为false，找到就是true
                    ResultSet resultSet = statement.executeQuery(select);

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                        statement.executeUpdate(delete);
                    }
                    resultSet.close();
                    statement.close();
                    connection.close();

                    break;
                }
                case "mysql": {
                    connection.prepareStatement(createTable).executeUpdate();

                    ResultSet resultSet = connection.prepareStatement(select).executeQuery();

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                        connection.prepareStatement(delete).executeUpdate();
                    }
                    resultSet.close();
                    connection.close();
                    break;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static long getBind(String id,String mode, Database db) {
        long qq = 0L;

        String createTable = "CREATE TABLE IF NOT EXISTS whitelist (id TINYTEXT NOT NULL, qq long NOT NULL);";
        String select = "SELECT * FROM whitelist WHERE id='" + id + "' LIMIT 1;";

        try {
            Connection connection = db.getConnection();
            switch (mode){
                case "mysql": {
                    connection.prepareStatement(createTable).executeUpdate();

                    ResultSet resultSet = connection.prepareStatement(select).executeQuery();

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                    }
                    qq = resultSet.getLong("qq");
                    resultSet.close();
                    connection.close();
                    break;
                }
                case "sqlite":
                default: {
                    Statement statement = connection.createStatement();
                    statement.executeUpdate(createTable);

                    ResultSet resultSet = statement.executeQuery(select);

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                    }
                    qq = resultSet.getLong("qq");
                    resultSet.close();
                    statement.close();
                    connection.close();
                    break;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return qq;
    }
    
    @Nullable
    public static String getBind(long qq,String mode, Database db) {
        String id = null;

        String createTable = "CREATE TABLE IF NOT EXISTS whitelist (id TINYTEXT NOT NULL, qq long NOT NULL);";
        String select = "SELECT * FROM whitelist WHERE qq=" + qq + " LIMIT 1;";

        try {
            Connection connection = db.getConnection();
            switch (mode){
                case "mysql": {
                    connection.prepareStatement(createTable).executeUpdate();

                    ResultSet resultSet = connection.prepareStatement(select).executeQuery();

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                    }
                    id = resultSet.getString("id");
                    resultSet.close();
                    connection.close();
                    break;
                }
                case "sqlite":
                default: {
                    Statement statement = connection.createStatement();
                    statement.executeUpdate(createTable);

                    ResultSet resultSet = statement.executeQuery(select);

                    if (resultSet.isBeforeFirst()) {
                        resultSet.next();
                    }
                    id = resultSet.getString("id");
                    resultSet.close();
                    statement.close();
                    connection.close();

                    break;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return id;
    }
}
