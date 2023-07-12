package org.linear.linearbot.event.server;

import com.griefdefender.api.event.ClaimEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class GDClaimEvent implements Listener {


    private static String gdMessage;

    @EventHandler(priority = EventPriority.HIGH)
    public void onGDClaim (ClaimEvent e){
        setGDMessage(e.getMessage().toString());
    }

    public static String getGDMessage() {return gdMessage;}
    private static void setGDMessage(String s) {gdMessage = s;}
}
