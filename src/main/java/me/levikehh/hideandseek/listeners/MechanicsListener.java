package me.levikehh.hideandseek.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import me.levikehh.hideandseek.HideAndSeek;
import me.levikehh.hideandseek.managers.MechanicsManager;
import me.levikehh.hideandseek.models.BlockPosition;
import me.levikehh.hideandseek.models.Match;

public class MechanicsListener implements Listener {
    private final HideAndSeek plugin;
    private final MechanicsManager mechanics;

    public MechanicsListener(HideAndSeek plugin) {
        this.plugin = plugin;
        this.mechanics = plugin.getMechanicsManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Match match = this.plugin.getGameManager().getMatch(player);

        if (match == null) {
            return;
        }

        boolean isHider = this.plugin.getTeamManager().isHider(match, player);
        boolean isSeeker = this.plugin.getTeamManager().isSeeker(match, player);

        if (isHider) {
            this.mechanics.handleHiderMove(player, event.getFrom(), event.getTo());

            if (this.mechanics.isHiderLocked(player) && this.checkIsMoved(event)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.teleport(event.getFrom()));
            }
        }

        if (isSeeker && this.mechanics.isSeekerLocked(player) && this.checkIsMoved(event)) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> player.teleport(event.getFrom()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        this.mechanics.handleHiderMove(player, event.getFrom(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSuffocation(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION && this.mechanics.isHiderLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Match match = this.plugin.getGameManager().getMatch(player);
        if (match == null) {
            return;
        }

        boolean isSeeker = this.plugin.getTeamManager().isSeeker(match, player);

        if (isSeeker) {
            if (event.getClickedBlock() == null) {
                return;
            }

            Block block = event.getClickedBlock();
            if (!this.mechanics.getSolidBlockOwners().containsKey(BlockPosition.of(block))) {
                return;
            }

            event.setCancelled(true);
            this.mechanics.hitSolidBlock(block, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerArmSwing(PlayerArmSwingEvent event) {
        Player player = event.getPlayer();
        Match match = this.plugin.getGameManager().getMatch(player);
        if (match == null) {
            return;
        }

        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        boolean isHider = this.plugin.getTeamManager().isHider(match, player);

        if (!isHider) {
            return;
        }
        if (!this.mechanics.isHiderLocked(player)) {
            return;
        }
        this.mechanics.forceExitSolidHider(player, "self_release");
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.mechanics.handleLeave(player);
    }

    private boolean checkIsMoved(PlayerMoveEvent event) {
        return event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();
    }
}
