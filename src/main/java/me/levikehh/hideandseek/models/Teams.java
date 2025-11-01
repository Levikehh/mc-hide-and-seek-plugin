package me.levikehh.hideandseek.models;

import java.util.List;

import org.bukkit.entity.Player;

public record Teams(List<Player> hiders, List<Player> seekers) {
    public static int getSeekerCount(int playerCount, int configDefault, int maxSeekerCount) {
        if (playerCount <= 1)
            return 0;
        if (playerCount == 2)
            return 1;
        if (playerCount <= 5)
            return 1;
        if (playerCount <= 7)
            return 2;

        int defaultOrRequested = (maxSeekerCount > 0)
                ? maxSeekerCount
                : configDefault;

        return Math.min(defaultOrRequested, playerCount);
    }
}
