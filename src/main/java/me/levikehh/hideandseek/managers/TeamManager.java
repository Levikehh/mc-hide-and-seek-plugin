package me.levikehh.hideandseek.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import me.levikehh.hideandseek.HideAndSeek;
import me.levikehh.hideandseek.models.Match;
import me.levikehh.hideandseek.models.PlayerRole;

public class TeamManager {
    private static TeamManager instance;

    private final HideAndSeek plugin;

    private final Map<String, Map<UUID, PlayerRole>> roleByMatch;
    private final Map<String, List<UUID>> hidersByMatch;
    private final Map<String, List<UUID>> seekersByMatch;

    private TeamManager(HideAndSeek plugin) {
        this.plugin = plugin;
        this.roleByMatch = new HashMap<>();
        this.hidersByMatch = new HashMap<>();
        this.seekersByMatch = new HashMap<>();
    }

    public static TeamManager getInstance(HideAndSeek plugin) {
        if (instance == null) {
            instance = new TeamManager(plugin);
        }

        return instance;
    }

    public void assignTeams(Match match, List<Player> players) {
        TeamSplit teams = this.splitPlayers(players);

        match.setHiders(teams.hiders());
        match.setSeekers(teams.seekers());

        Map<UUID, PlayerRole> roleMap = new HashMap<>();
        List<UUID> hiderIds = new ArrayList<>();
        List<UUID> seekerIds = new ArrayList<>();

        for (Player hider : teams.hiders()) {
            roleMap.put(hider.getUniqueId(), PlayerRole.HIDER);
            hiderIds.add(hider.getUniqueId());
        }

        for (Player seeker : teams.seekers()) {
            roleMap.put(seeker.getUniqueId(), PlayerRole.SEEKER);
            seekerIds.add(seeker.getUniqueId());
        }

        String matchId = match.getId();
        this.roleByMatch.put(matchId, roleMap);
        this.hidersByMatch.put(matchId, hiderIds);
        this.seekersByMatch.put(matchId, seekerIds);

        // TODO apply team effects
    }

    public void convertHiderToSeeker(Match match, Player player) {
        String matchId = match.getId();

        Map<UUID, PlayerRole> roleMap = this.roleByMatch.get(matchId);
        List<UUID> hiderIds = this.hidersByMatch.get(matchId);
        List<UUID> seekerIds = this.seekersByMatch.get(matchId);

        if (roleMap == null || hiderIds == null || seekerIds == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        hiderIds.remove(playerId);

        if (!seekerIds.contains(playerId)) {
            seekerIds.add(playerId);
        }

        roleMap.put(playerId, PlayerRole.SEEKER);
        match.removePlayer(player);
        match.addSeeker(player);

        // TODO reset hider disguise, give seeker kit
    }

    public void removePlayer(Match match, Player player) {
        String matchId = match.getId();

        Map<UUID, PlayerRole> roleMap = this.roleByMatch.get(matchId);
        List<UUID> hiderIds = this.hidersByMatch.get(matchId);
        List<UUID> seekerIds = this.seekersByMatch.get(matchId);

        if (roleMap != null) {
            roleMap.remove(player.getUniqueId());
        }
        if (hiderIds != null) {
            hiderIds.remove(player.getUniqueId());
        }
        if (seekerIds != null) {
            seekerIds.remove(player.getUniqueId());
        }

        match.removePlayer(player);

        // TODO check for early win
    }

    public void clearMatchData(Match match) {
        String matchId = match.getId();

        this.roleByMatch.remove(matchId);
        this.hidersByMatch.remove(matchId);
        this.seekersByMatch.remove(matchId);
    }

    public PlayerRole getRole(Match match, Player player) {
        Map<UUID, PlayerRole> roleMap = this.roleByMatch.get(match.getId());
        if (roleMap == null) {
            return null;
        }

        return roleMap.get(player.getUniqueId());
    }

    public boolean isHider(Match match, Player player) {
        return this.getRole(match, player) == PlayerRole.HIDER;
    }

    public boolean isSeeker(Match match, Player player) {
        return this.getRole(match, player) == PlayerRole.SEEKER;
    }

    private record TeamSplit(List<Player> hiders, List<Player> seekers) {
    }

    private TeamSplit splitPlayers(List<Player> players) {
        int playerCount = players.size();
        int seekerCount = this.getSeekerCount(playerCount, 3);

        List<Player> shuffled = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < seekerCount; i++) {
            int j = i + random.nextInt(playerCount - i);
            Collections.swap(shuffled, i, j);
        }

        List<Player> hiders = new ArrayList<>(shuffled.subList(seekerCount, playerCount));
        List<Player> seekers = new ArrayList<>(shuffled.subList(0, seekerCount));

        return new TeamSplit(hiders, seekers);
    }

    private int getSeekerCount(int playerCount, int maxSeekerCount) {
        if (playerCount <= 1)
            return 0;
        if (playerCount == 2)
            return 1;
        if (playerCount <= 5)
            return 1;
        if (playerCount <= 7)
            return 2;

        int configDefault = this.plugin.config().match().maxSeeker();
        int defaultOrRequested = (maxSeekerCount > 0)
                ? Math.min(configDefault, maxSeekerCount)
                : configDefault;

        return Math.min(defaultOrRequested, playerCount);
    }
}
