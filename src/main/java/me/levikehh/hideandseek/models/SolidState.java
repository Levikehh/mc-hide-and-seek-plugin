package me.levikehh.hideandseek.models;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public final class SolidState {
    public final BlockPosition position;
    public final BlockData previousBlockData;
    public final Material material;

    public int health;
    public long cooldownUntilMs;

    public SolidState(BlockPosition position, BlockData previousBlockData, Material material, int health, long cooldownUntilMs) {
        this.position = position;
        this.previousBlockData = previousBlockData;
        this.material = material;
        this.health = health;
        this.cooldownUntilMs = cooldownUntilMs;
    }
}
