package me.regadpole.plumbot.tool;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringTool {

    public static String filterColor(String text){

        String regEx = "§[0-9a-zA-Z]";
        Pattern p = Pattern.compile(regEx);
        Matcher matcher = p.matcher(text);
        return matcher.replaceAll("").trim();

    }
}
