package me.levikehh.hideandseek.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import me.levikehh.hideandseek.HideAndSeek;
import me.levikehh.hideandseek.models.Lobby;
import me.levikehh.hideandseek.models.Match;

public class GameManager {
    private static GameManager instance;

    private final HideAndSeek plugin;
    private final MatchManager matchManager;
    private final LobbyManager lobbyManager;

    private final Map<UUID, Lobby> playerLobbyIndex;
    private final Map<UUID, Match> playerMatchIndex;

    public static GameManager getInstance(HideAndSeek plugin) {
        if (instance == null) {
            instance = new GameManager(plugin);
        }

        return instance;
    }

    private GameManager(HideAndSeek plugin) {
        this.plugin = plugin;
        this.matchManager = plugin.getMatchManager();
        this.lobbyManager = plugin.getLobbyManager();

        this.playerLobbyIndex = new HashMap<>();
        this.playerMatchIndex = new HashMap<>();
    }

    public void addToLobby(Player player) {
        Lobby lobby = this.lobbyManager.getOrCreateLobby(player);
        this.lobbyManager.joinLobby(player, lobby);
        this.playerLobbyIndex.put(player.getUniqueId(), lobby);
    }

    public void removeFromLobby(Player player) {
        Lobby lobby = this.playerLobbyIndex.get(player.getUniqueId());

        if (lobby == null) {
            return;
        }

        this.lobbyManager.leaveLobby(player, lobby);
        this.playerLobbyIndex.remove(player.getUniqueId());
    }

    public void handleDisconnect(Player player) {
        Match match = this.playerMatchIndex.get(player.getUniqueId());
        Lobby lobby = this.playerLobbyIndex.get(player.getUniqueId());

        if (match != null) {
            this.matchManager.handleDisconnect(player, match);
            this.playerMatchIndex.remove(player.getUniqueId());
        }

        if (lobby != null) {
            this.lobbyManager.handleDisconnect(player, lobby);
            this.playerLobbyIndex.remove(player.getUniqueId());
        }
    }

    public void onMatchStarted(Lobby lobby, Match match) {
        for (Player player : lobby.getPlayers()) {
            this.playerMatchIndex.put(player.getUniqueId(), match);
        }
    }

    public void onMatchEnded(Lobby lobby, Match match) {
        for (Player player : lobby.getPlayers()) {
            this.playerMatchIndex.remove(player.getUniqueId());
        }
    }

    public Lobby getLobby(Player player) {
        return this.playerLobbyIndex.get(player.getUniqueId());
    }

    public Match getMatch(Player player) {
        return this.playerMatchIndex.get(player.getUniqueId());
    }

    public boolean isInLobby(Player player) {
        return this.getLobby(player) != null;
    }

    public boolean isInMatch(Player player) {
        return this.getMatch(player) != null;
    }
}
