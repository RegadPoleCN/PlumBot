package me.regadpole.plumbot.internal;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.internal.database.Database;
import me.regadpole.plumbot.internal.database.DatabaseManager;

import java.util.List;

public class WhitelistHelper {

    public static boolean checkCount(String qq){
        List<String> idList = DatabaseManager.getBind(qq, DataBase.type().toLowerCase(), PlumBot.getDatabase());
        int maxCount = Config.WhiteListMaxCount();
        if (idList.isEmpty()) return true;
        return idList.size() < maxCount;
    }

    public static boolean checkIDNotExist(String id){
        return DatabaseManager.getBindId(id, DataBase.type().toLowerCase(), PlumBot.getDatabase()) == 0L;
    }

    public static List<String> addAndGet(String id, String qq, String mode, Database db){
        DatabaseManager.addBind(id, qq, mode, db);
        return DatabaseManager.getBind(qq, mode, db);
    }

    public static List<String> removeAndGet(String qq, int num, String mode, Database db){
        DatabaseManager.removeBind(qq, num, mode, db);
        return DatabaseManager.getBind(qq, mode, db);
    }
}
