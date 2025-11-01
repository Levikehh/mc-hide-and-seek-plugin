package me.levikehh.hideandseek.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import me.levikehh.hideandseek.HideAndSeek;

public class PlayerDisconnectListener implements Listener {
    private final HideAndSeek plugin;

    public PlayerDisconnectListener(HideAndSeek plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.plugin.getGameManager().handleDisconnect(player);
    }
}
