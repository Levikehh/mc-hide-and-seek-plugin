package me.levikehh.hideandseek.models;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class BlockPosition {
    public final String world;
    public final int x;
    public final int y;
    public final int z;

    public BlockPosition(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockPosition of(Block block) {
        return new BlockPosition(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public Location asLocation() {
        World world = Bukkit.getWorld(this.world);
        return new Location(world, this.x, this.y, this.z);
    }

    public Block toBlock() {
        World world = Bukkit.getWorld(this.world);
        return world.getBlockAt(this.x, this.y, this.z);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof BlockPosition other))
            return false;
        return this.x == other.x && this.y == other.y && this.z == other.z && Objects.equals(this.world, other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.world, this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return this.world + ":" + this.x + "," + this.y + "," + this.z;
    }
}
