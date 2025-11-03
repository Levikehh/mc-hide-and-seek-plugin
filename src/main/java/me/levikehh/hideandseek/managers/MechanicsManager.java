package me.levikehh.hideandseek.managers;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import me.levikehh.hideandseek.models.HiderForm;
import me.levikehh.hideandseek.models.HiderStatus;
import me.levikehh.hideandseek.models.SolidState;

public class MechanicsManager {
    private static MechanicsManager instance;

    private final HideAndSeek plugin;
    private final Predicate<Player> isHider;
    private final Predicate<Player> isSeeker;
    // HIDER
    private final Map<UUID, HiderStatus> hiders;
    private final Map<BlockPosition, UUID> solidBlockOwners;
    private final Map<UUID, Float> prevHiderWalkSpeeds;

    // SEEKER
    private final Set<UUID> lockedSeekers;
    private final Map<UUID, Float> prevSeekerWalkSpeeds;

    private MechanicsManager(HideAndSeek plugin, Predicate<Player> isHider, Predicate<Player> isSeeker) {
        this.plugin = plugin;
        this.isHider = isHider;
        this.isSeeker = isSeeker;

        this.hiders = new HashMap<>();
        this.solidBlockOwners = new HashMap<>();
        this.prevHiderWalkSpeeds = new HashMap<>();

        this.lockedSeekers = new HashSet<>();
        this.prevSeekerWalkSpeeds = new HashMap<>();

        this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> this.mechanicsTick(), 5L, 5L);
    }

    public static MechanicsManager getInstance(HideAndSeek plugin, Predicate<Player> isHider,
            Predicate<Player> isSeeker) {
        if (instance == null) {
            instance = new MechanicsManager(plugin, isHider, isSeeker);
        }

        return instance;
    }

    private void mechanicsTick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, HiderStatus> entry : this.hiders.entrySet()) {
            UUID playerId = entry.getKey();
            HiderStatus status = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }

            // TODO check if we ever get log from here, if so we have an issue
            if (!this.isHider.test(player)) {
                this.plugin.getLogger().warning(
                        "In mechanicsTick we just iterated through a non-hider player. (List should only contain hiders)");
                continue;
            }

            if (status.form == HiderForm.MOVING) {
                long stillFor = now - status.lastMovedAtMs;
                long timeLeftUntilSolid = (long) (this.plugin.config().teams().hiders().timeToSolid() * 1000)
                        - stillFor;
                if (timeLeftUntilSolid <= 0 && now >= status.nextAllowedSolidifyAt) {
                    this.enterLockInternal(player, status);
                    continue;
                }

                float progress = stillFor / (float) (this.plugin.config().teams().hiders().timeToSolid() * 1000f);
                progress = Math.clamp(progress, 0.0f, 1.0f);

                player.setExp(progress);
                player.setLevel(0);
            } else if (status.form == HiderForm.LOCKED) {
                long remainingCooldownMs = status.nextAllowedSolidifyAt - now;
                remainingCooldownMs = Math.max(remainingCooldownMs, 0);

                float progress = 1.0f
                        - (remainingCooldownMs
                                / (float) (this.plugin.config().teams().hiders().solidifyCooldown() * 1000f));
                progress = Math.clamp(progress, 0.0f, 1.0f);

                player.setExp(progress);
                player.setLevel(0);
            }
        }
    }

    public void applyHiderDisguise(Player player) {
        HiderStatus status = this.hiders.computeIfAbsent(player.getUniqueId(), (id) -> {
            HiderStatus newStatus = new HiderStatus();
            newStatus.form = HiderForm.MOVING;
            newStatus.lastMovedAtMs = System.currentTimeMillis();
            newStatus.nextAllowedSolidifyAt = 0;
            newStatus.originalExperience = player.getExp();
            newStatus.originalLevel = player.getLevel();
            return newStatus;
        });

        if (status.form == HiderForm.LOCKED) {
            this.exitLockInternal(player, status, false, false);
        }

        player.setInvisible(true);
        player.setCollidable(false);

        if (status.blockDisplayId == null || this.findEntityByUUID(status.blockDisplayId) == null) {
            BlockDisplay displayEntity = this.spawnDisguiseEntityAt(this.normalizeLocation(player.getLocation()));
            status.blockDisplayId = displayEntity.getUniqueId();
        } else {

        }

        status.form = HiderForm.MOVING;
        status.lastMovedAtMs = System.currentTimeMillis();
    }

    public void handleHiderMove(Player player, Location from, Location to) {
        if (!this.isHider.test(player)) {
            return;
        }

        HiderStatus status = this.hiders.get(player.getUniqueId());
        if (status == null) {
            return;
        }

        boolean isMoved = checkIsMoved(from, to);
        if (status.form == HiderForm.LOCKED) {
            return;
        }

        long now = System.currentTimeMillis();
        if (isMoved) {
            status.lastMovedAtMs = now;
        }

        if (status.blockDisplayId != null) {
            this.updateDisguisePosition(status.blockDisplayId, to);
        }
    }

    public void forceExitSolidHider(Player player, String reason) {
        HiderStatus status = this.hiders.get(player.getUniqueId());
        if (status == null) {
            return;
        }

        if (status.form != HiderForm.LOCKED) {
            return;
        }

        this.exitLockInternal(player, status, true, true);
        this.plugin.getLogger().info("Hider " + player.getName() + " broke cover: " + reason);
    }

    public UUID hitSolidBlock(Block block, Player attacker) {
        BlockPosition position = BlockPosition.of(block);
        UUID ownerId = this.solidBlockOwners.get(position);
        if (ownerId == null) {
            return null;
        }

        Player hider = Bukkit.getPlayer(ownerId);
        if (hider == null) {
            this.cleanupOrphanBlocks(position);
            return null;
        }
        HiderStatus status = this.hiders.get(ownerId);
        SolidState solidState = status != null ? status.solidState : null;

        if (status == null || solidState == null || status.form != HiderForm.LOCKED) {
            this.cleanupOrphanBlocks(position);
            return null;
        }

        Location effectLocation = position.asLocation().add(0.5, 0.05, 0.5);
        effectLocation.getWorld().playSound(effectLocation, Sound.BLOCK_STONE_HIT, 0.8f, 1.2f);
        effectLocation.getWorld().spawnParticle(Particle.BLOCK, effectLocation, 10,
                solidState.material.createBlockData());

        solidState.health--;
        if (solidState.health <= 0) {
            this.forceExitSolidHider(hider, "broken cover (health <= 0)");
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

    public boolean isHiderLocked(Player player) {
        HiderStatus status = this.hiders.get(player.getUniqueId());
        return status != null && status.form == HiderForm.LOCKED;
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
        UUID playerId = player.getUniqueId();

        HiderStatus status = this.hiders.get(playerId);
        if (status != null) {
            if (status.form == HiderForm.LOCKED) {
                this.exitLockInternal(player, status, false, false);
            }

            this.removeDisguiseEntity(status);
            player.setInvisible(false);
            player.setCollidable(true);
            player.setExp(status.originalExperience);
            player.setLevel(status.originalLevel);
            this.hiders.remove(playerId);
        }

        this.unlockSeeker(player);
    }

    public void cleanupMatchPlayers(Collection<? extends Player> players) {
        for (Player player : players) {
            this.handleLeave(player);
        }
    }

    public Map<BlockPosition, UUID> getSolidBlockOwners() {
        return this.solidBlockOwners;
    }

    /* INTERNAL */

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

    private void updateDisguisePosition(UUID blockDisplayId, Location to) {
        Entity disguiseEntity = this.findEntityByUUID(blockDisplayId);
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

    private void removeDisguiseEntity(HiderStatus status) {
        if (status.blockDisplayId == null) {
            return;
        }

        Entity entity = this.findEntityByUUID(status.blockDisplayId);
        if (entity != null) {
            entity.remove();
        }

        status.blockDisplayId = null;
    }

    private void enterLockInternal(Player player, HiderStatus status) {
        if (status.form == HiderForm.LOCKED) {
            return;
        }

        Location location = player.getLocation();
        Block base = location.getBlock();
        Block above = base.getRelative(0, 1, 0);
        Block below = base.getRelative(0, -1, 0);

        if (!base.isEmpty() || !above.isEmpty() || below.getType() == Material.AIR) {
            return;
        }

        Material disguiseMaterial = Material.COBBLESTONE;
        BlockData previousBlockData = base.getBlockData();

        this.removeDisguiseEntity(status);
        player.setInvisible(true);
        player.setCollidable(false);

        if (!this.prevHiderWalkSpeeds.containsKey(player.getUniqueId())) {
            this.prevHiderWalkSpeeds.put(player.getUniqueId(), player.getWalkSpeed());
        }

        player.setWalkSpeed(0.0f);

        BlockPosition position = BlockPosition.of(base);
        SolidState solidState = new SolidState(position, previousBlockData, disguiseMaterial,
                this.plugin.config().teams().hiders().health(), 0L);

        status.solidState = solidState;
        status.form = HiderForm.LOCKED;

        this.solidBlockOwners.put(position, player.getUniqueId());

        this.sendFakeBlockToOthers(player, status, false);

        Location effectLocation = position.asLocation().add(0.5, 0.05, 0.5);
        effectLocation.getWorld().playSound(effectLocation, Sound.BLOCK_STONE_PLACE, 0.8f, 1f);
        effectLocation.getWorld().spawnParticle(Particle.BLOCK, effectLocation, 8, disguiseMaterial.createBlockData());
    }

    private void exitLockInternal(Player player, HiderStatus status, boolean playEffects, boolean startCooldown) {
        if (status.form != HiderForm.LOCKED) {
            return;
        }

        SolidState solidState = status.solidState;
        if (solidState != null) {
            Block block = solidState.position.toBlock();
            if (block.getType() == solidState.material) {
                this.sendFakeBlockToOthers(player, status, true);
            }

            this.solidBlockOwners.remove(solidState.position);

            if (playEffects) {
                Location effectLocation = solidState.position.asLocation().add(0, 0, 0);
                effectLocation.getWorld().playSound(effectLocation, Sound.BLOCK_STONE_BREAK, 0.8f, 1.2f);
                effectLocation.getWorld().spawnParticle(Particle.BLOCK, effectLocation, 10,
                        solidState.material.createBlockData());
            }
        }

        status.solidState = null;
        status.form = HiderForm.MOVING;

        Float prevWalkSpeed = this.prevHiderWalkSpeeds.get(player.getUniqueId());
        if (prevWalkSpeed != null) {
            player.setWalkSpeed(prevWalkSpeed);
        } else {
            player.setWalkSpeed(0.2f);
        }

        this.prevHiderWalkSpeeds.remove(player.getUniqueId());

        player.setInvisible(true);
        player.setCollidable(false);
        BlockDisplay displayEntity = this.spawnDisguiseEntityAt(this.normalizeLocation(player.getLocation()));
        status.blockDisplayId = displayEntity.getUniqueId();

        long now = System.currentTimeMillis();
        status.lastMovedAtMs = now;

        if (startCooldown) {
            status.nextAllowedSolidifyAt = now
                    + (long) (this.plugin.config().teams().hiders().solidifyCooldown() * 1000);
        }
    }

    private void sendFakeBlockToOthers(Player hider, HiderStatus status, boolean sendOriginal) {
        if (status.solidState == null) {
            return;
        }

        Location location = status.solidState.position.asLocation();
        if (location == null) {
            return;
        }

        List<Player> players = this.plugin.getGameManager().getLobby(hider).getPlayers();
        if (players != null) {
            for (Player other : players) {
                if (other.equals(hider)) {
                    continue;
                }

                if (other.getWorld() != hider.getWorld()) {
                    continue;
                }

                BlockData materialToSend = !sendOriginal
                        ? status.solidState.material.createBlockData()
                        : status.solidState.previousBlockData;

                other.sendBlockChange(location, materialToSend);
            }
        }
    }

    private void cleanupOrphanBlocks(BlockPosition position) {
        UUID ownerId = this.solidBlockOwners.remove(position);
        if (ownerId == null) {
            return;
        }

        HiderStatus status = this.hiders.get(ownerId);
        if (status != null && status.solidState != null && status.solidState.position.equals(position)) {
            status.solidState = null;
            status.form = HiderForm.MOVING;
        }
    }

    private Location normalizeLocation(Location location) {
        Location result = location.clone();

        result.setYaw(0f);
        result.setPitch(0f);

        double x = Math.floor(result.getX());
        double y = Math.floor(result.getY());
        double z = Math.floor(result.getZ());

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
