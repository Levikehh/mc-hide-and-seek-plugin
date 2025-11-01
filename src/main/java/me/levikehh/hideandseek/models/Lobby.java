package me.levikehh.hideandseek.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

public class Lobby {
    private final String id;
    private final String name;
    private final List<Player> players;
    private Match match;
    private LobbyState state;

    public enum LobbyState {
        WAITING, // waiting for players to join the lobby
        STARTED, // the match started
        FINISHED, // the match ended
    }

    public Lobby(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name == null ? this.id : name;
        this.players = new ArrayList<>();
        this.state = LobbyState.WAITING;
    }

    public String getId() {
        return this.id;
    }

    public LobbyState getState() {
        return this.state;
    }

    public void setState(LobbyState newState) {
        this.state = newState;
    }

    public String getName() {
        return this.name;
    }

    public void addPlayer(Player player) {
        if (this.players.contains(player)) {
            return;
        }

        this.players.add(player);
    }

    public void removePlayer(Player player) {
        this.players.remove(player);
    }

    public boolean isParticipating(Player player) {
        return this.players.contains(player);
    }

    public List<Player> getPlayers() {
        return this.players;
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public void setMatch(Match match){
        this.match = match;
    }

    public Match getMatch() {
        return this.match;
    }
}
