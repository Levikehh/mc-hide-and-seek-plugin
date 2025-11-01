package me.levikehh.hideandseek.managers;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import me.levikehh.hideandseek.HideAndSeek;
import me.levikehh.hideandseek.models.BlockPosition;
import me.levikehh.hideandseek.models.SolidState;

public class MechanicsManager {
    private static MechanicsManager instance;

    private final HideAndSeek plugin;
    private final Predicate<Player> isHider;
    private final Predicate<Player> isSeeker;
    // HIDER
    private final Map<UUID, UUID> disguiseEntityByPlayer;
    private final Map<UUID, SolidState> solidBlockByPlayer;
    private final Map<UUID, Long> lastMovedAtMS;
    private final Map<BlockPosition, UUID> disguiseAtLocation;

    // SEEKER
    private final Set<UUID> lockedSeekers;
    private final Map<UUID, Float> prevSeekerWalkSpeeds;

    private MechanicsManager(HideAndSeek plugin, Predicate<Player> isHider, Predicate<Player> isSeeker) {
        this.plugin = plugin;
        this.isHider = isHider;
        this.isSeeker = isSeeker;
        this.disguiseEntityByPlayer = new HashMap<>();
        this.lastMovedAtMS = new HashMap<>();
        this.disguiseAtLocation = new HashMap<>();
        this.solidBlockByPlayer = new HashMap<>();
        this.lockedSeekers = new HashSet<>();
        this.prevSeekerWalkSpeeds = new HashMap<>();
    }

    public static MechanicsManager getInstance(HideAndSeek plugin, Predicate<Player> isHider,
            Predicate<Player> isSeeker) {
        if (instance == null) {
            instance = new MechanicsManager(plugin, isHider, isSeeker);
        }

        return instance;
    }

    public void applyHiderDisguise(Player player) {
        this.removeHiderDisguise(player);

        player.setInvisible(true);

        BlockDisplay displayEntity = this.spawnDisguiseEntityAt(this.normalizeLocation(player.getLocation()));

        this.disguiseEntityByPlayer.put(player.getUniqueId(), displayEntity.getUniqueId());
        this.lastMovedAtMS.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void handleHiderMove(Player player, Location from, Location to) {
        if (!this.isHider.test(player)) {
            return;
        }

        if (this.isSolid(player)) {
            return;
        }

        long now = System.currentTimeMillis();

        if (this.checkIsMoved(from, to)) {
            this.lastMovedAtMS.put(player.getUniqueId(), now);
        }

        this.updateDisguisePosition(player, to);

        if (!this.solidBlockByPlayer.containsKey(player.getUniqueId())) {
            this.maybeAutoSolidify(player, now);
        }
    }

    public void removeHiderDisguise(Player player) {
        this.removeDisguiseEntity(player);
        player.setInvisible(false);
        this.disguiseEntityByPlayer.remove(player.getUniqueId());
        lastMovedAtMS.remove(player.getUniqueId());
    }

    public boolean solidify(Player player) {
        if (!isHider.test(player)) {
            return false;
        }

        if (this.isSolid(player)) {
            return false;
        }

        Material material = Material.COBBLESTONE;
        Block base = player.getLocation().getBlock();
        Block above = base.getRelative(0, 1, 0);

        if (!base.isEmpty() || !above.isEmpty()) {
            return false;
        }

        // Disallow solidify on certain blocks
        Block under = base.getRelative(0, -1, 0);
        if (under.getType() == Material.AIR) {
            return false;
        }

        BlockPosition position = BlockPosition.of(base);
        if (this.disguiseAtLocation.containsKey(position)) {
            return false;
        }

        BlockData previousBlockData = base.getBlockData();
        base.setBlockData(material.createBlockData(), false);

        this.removeDisguiseEntity(player);

        SolidState solidBlock = new SolidState(position, previousBlockData, material, 3,
                System.currentTimeMillis() + 2000);

        this.solidBlockByPlayer.put(player.getUniqueId(), solidBlock);
        this.disguiseAtLocation.put(position, player.getUniqueId());

        Location fxLocation = position.asLocation().add(0.5, 0.05, 0.5);
        fxLocation.getWorld().playSound(fxLocation, Sound.BLOCK_STONE_PLACE, 0.8f, 1f);
        fxLocation.getWorld().spawnParticle(Particle.BLOCK, fxLocation, 8, material.createBlockData());

        return true;
    }

    public void desolidify(Player player) {
        SolidState solidState = this.solidBlockByPlayer.get(player.getUniqueId());

        if (solidState != null) {
            Block block = solidState.position.toBlock();
            if (block.getType() == solidState.material) {
                block.setBlockData(solidState.previousBlockData, false);
            }

            this.disguiseAtLocation.remove(solidState.position);

            solidState.cooldownUntilMs = System.currentTimeMillis() + 2000;
        }

        if (isHider.test(player)) {
            player.setInvisible(true);

            BlockDisplay blockDisplay = this.spawnDisguiseEntityAt(this.normalizeLocation(player.getLocation()));
            this.disguiseEntityByPlayer.put(player.getUniqueId(), blockDisplay.getUniqueId());

            player.setVelocity(
                    player.getLocation().getDirection().normalize().multiply(0.2).setY(0.1));
        } else {
            player.setInvisible(false);
            this.removeDisguiseEntity(player);
        }
    }

    public boolean isSolid(Player player) {
        return this.solidBlockByPlayer.containsKey(player.getUniqueId());
    }

    public UUID hitSolidBlock(Block block, Player attacker) {
        BlockPosition position = BlockPosition.of(block);
        UUID ownerId = this.disguiseAtLocation.get(position);
        if (ownerId == null) {
            return null;
        }

        Player hider = Bukkit.getPlayer(ownerId);
        SolidState solidState = this.solidBlockByPlayer.get(ownerId);

        if (hider == null || solidState == null) {
            this.disguiseAtLocation.remove(position);
            this.solidBlockByPlayer.remove(ownerId);
            return null;
        }

        Location fxLoc = position.asLocation().add(0.5, 0.05, 0.5);
        fxLoc.getWorld().playSound(fxLoc, Sound.BLOCK_STONE_HIT, 0.8f, 1.2f);
        fxLoc.getWorld().spawnParticle(Particle.BLOCK, fxLoc, 10, solidState.material.createBlockData());

        solidState.health--;
        if (solidState.health == 0) {
            this.desolidify(hider);
            return ownerId;
        }

        return ownerId;
    }

    public void lockSeeker(Player player) {
        if (!isSeeker.test(player)) {
            return;
        }

        if (this.lockedSeekers.contains(player.getUniqueId())) {
            return;
        }

        this.lockedSeekers.add(player.getUniqueId());

        this.prevSeekerWalkSpeeds.put(player.getUniqueId(), player.getWalkSpeed());
        player.setWalkSpeed(0.0f);

        this.applySeekerBlindness(player);
    }

    public void unlockSeeker(Player player) {
        if (!this.lockedSeekers.remove(player.getUniqueId())) {
            return;
        }

        Float previousWalkSpeed = this.prevSeekerWalkSpeeds.get(player.getUniqueId());
        if (previousWalkSpeed != null) {
            player.setWalkSpeed(previousWalkSpeed);
        }

        this.clearSeekerBlindness(player);
    }

    public void unlockAllSeekers(Collection<? extends Player> players) {
        for (Player player : players) {
            this.unlockSeeker(player);
        }

        this.lockedSeekers.clear();
        this.prevSeekerWalkSpeeds.clear();
    }

    public boolean isSeekerLocked(Player player) {
        return this.lockedSeekers.contains(player.getUniqueId());
    }

    private void applySeekerBlindness(Player player) {
        int longDuration = 12000;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                longDuration,
                1,
                false,
                false,
                true));

        if (PotionEffectType.DARKNESS != null) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.DARKNESS,
                    longDuration,
                    1,
                    false,
                    false,
                    true));
        }
    }

    private void clearSeekerBlindness(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        if (PotionEffectType.DARKNESS != null) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    public void handleLeave(Player player) {
        if (this.isSolid(player)) {
            desolidify(player);
        }

        this.removeHiderDisguise(player);
        this.unlockSeeker(player);
    }

    public void cleanupMatchPlayers(Collection<? extends Player> players) {
        for (Player player : players) {
            this.handleLeave(player);
        }
    }

    public Map<BlockPosition, UUID> getDisguises() {
        return this.disguiseAtLocation;
    }

    /* INTERNAL */

    private void maybeAutoSolidify(Player player, long nowMS) {
        if (!isHider.test(player)) {
            return;
        }

        if (this.isSolid(player)) {
            return;
        }

        long lastMove = this.lastMovedAtMS.getOrDefault(player.getUniqueId(), nowMS);
        long stillFor = nowMS - lastMove;

        SolidState solidState = this.solidBlockByPlayer.get(player.getUniqueId());
        long cooldownUntil = solidState != null ? solidState.cooldownUntilMs : 0L;

        if (stillFor >= 3000 && nowMS >= cooldownUntil) {
            solidify(player);
        }
    }

    private BlockDisplay spawnDisguiseEntityAt(Location location) {
        Location normalized = this.normalizeLocation(location);

        BlockDisplay blockDisplay = (BlockDisplay) normalized.getWorld().spawnEntity(normalized,
                EntityType.BLOCK_DISPLAY);
        blockDisplay.setBlock(Material.COBBLESTONE.createBlockData());
        blockDisplay.setBillboard(Billboard.FIXED);
        blockDisplay.setViewRange(this.plugin.config().teams().hiders().viewRange());
        blockDisplay.setInterpolationDuration(2);
        blockDisplay.setBrightness(new Display.Brightness(15, 15));

        blockDisplay.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1f, 1f, 1f),
                new Quaternionf()));

        blockDisplay.setPersistent(false);

        return blockDisplay;
    }

    private void updateDisguisePosition(Player player, Location to) {
        UUID disguiseEntityId = this.disguiseEntityByPlayer.get(player.getUniqueId());
        if (disguiseEntityId == null) {
            return;
        }

        Entity disguiseEntity = this.findEntityByUUID(disguiseEntityId);
        if (!(disguiseEntity instanceof BlockDisplay blockDisplay)) {
            return;
        }

        Location location = this.normalizeLocation(to);
        blockDisplay.teleport(location);

        blockDisplay.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1f, 1f, 1f),
                new Quaternionf()));
    }

    private void removeDisguiseEntity(Player player) {
        UUID disguiseEntityId = this.disguiseEntityByPlayer.get(player.getUniqueId());
        if (disguiseEntityId == null) {
            return;
        }

        Entity disguiseEntity = this.findEntityByUUID(disguiseEntityId);
        if (disguiseEntity != null) {
            disguiseEntity.remove();
        }
    }

    private Location normalizeLocation(Location location) {
        Location result = location.clone();

        result.setYaw(0f);
        result.setPitch(0f);

        double x = Math.floor(result.getX()) + 0.5;
        double y = Math.floor(result.getY());
        double z = Math.floor(result.getZ()) + 0.5;

        result.setX(x);
        result.setY(y);
        result.setZ(z);

        return result;
    }

    private boolean checkIsMoved(Location from, Location to) {
        return from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
    }

    private Entity findEntityByUUID(UUID entityId) {
        for (World world : plugin.getServer().getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null)
                return entity;
        }
        return null;
    }
}
