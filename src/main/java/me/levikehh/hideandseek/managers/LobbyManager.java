package me.levikehh.hideandseek.managers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import hu.nomindz.devkit.utils.MessageBuilder;
import hu.nomindz.devkit.utils.TimeFormatter;
import hu.nomindz.devkit.utils.TimedTask;
import me.levikehh.hideandseek.HideAndSeek;
import me.levikehh.hideandseek.models.Lobby;
import me.levikehh.hideandseek.models.Lobby.LobbyState;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class LobbyManager {
    private static LobbyManager instance;

    private final HideAndSeek plugin;
    private final MatchManager matchManager;
    
    private final List<TimedTask<Lobby>> lobbies;

    private LobbyManager(HideAndSeek plugin) {
        this.plugin = plugin;
        this.matchManager = plugin.getMatchManager();

        this.lobbies = new ArrayList<>();
    }

    public static LobbyManager getInstance(HideAndSeek plugin) {
        if (instance == null) {
            instance = new LobbyManager(plugin);
        }

        return instance;
    }

    public Lobby getOrCreateLobby(Player player) {
        for (TimedTask<Lobby> task : this.lobbies) {
            Lobby lobby = task.getData();
            if (lobby.getState() == LobbyState.WAITING) {
                return lobby;
            }
        }

        return this.createLobby(player);
    }

    private Lobby createLobby(Player player) {
        Lobby lobby = new Lobby(null);

        TimedTask<Lobby> task = this.plugin.getTimer().startTimer(
                "lobby_" + lobby.getId(),
                lobby,
                this.plugin.config().lobby().duration(),
                (remainingSeconds) -> {
                    if (remainingSeconds <= 3 && remainingSeconds > 0) {
                        String message = ChatColor.GOLD + "" + ChatColor.BOLD + remainingSeconds;
                        for (Player lobbyPlayer : lobby.getPlayers()) {
                            lobbyPlayer.sendTitle(message, "", 0, 20, 10);
                            lobbyPlayer.playSound(lobbyPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        }
                    }

                    if (remainingSeconds > 0) {
                        String timerString = TimeFormatter.formatTime(remainingSeconds);
                        String actionBarString = String.format(
                                ChatColor.YELLOW + "Match starts in: %s" + ChatColor.GRAY + " | %d players",
                                timerString,
                                lobby.getPlayerCount());
                        for (Player lobbyPlayer : lobby.getPlayers()) {
                            lobbyPlayer.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarString));
                        }
                    }
                },
                () -> {
                    if (lobby.getPlayerCount() >= 2) {
                        this.matchManager.startMatch(lobby);
                    } else {
                        for (Player lobbyPlayer : lobby.getPlayers()) {
                            lobbyPlayer.spigot().sendMessage(new MessageBuilder()
                                    .addError("Not enough players to start a match.")
                                    .build());
                            this.leaveLobby(lobbyPlayer, lobby);
                        }
                    }
                }, true);

        this.lobbies.add(task);
        return lobby;
    }

    public void joinLobby(Player player, Lobby lobby) {
        if (lobby == null) {
            lobby = this.getOrCreateLobby(player);
        }

        if (!lobby.getPlayers().contains(player)) {
            lobby.addPlayer(player);

            TimedTask<Lobby> lobbyTask = this.getLobbyTask(lobby);
            int remainingSeconds = lobbyTask != null ? lobbyTask.getRemainingSeconds() : -1;
            player.spigot().sendMessage(new MessageBuilder()
                    .addSuccess("The match starts in ")
                    .addVariable(TimeFormatter.formatTimeReadable(remainingSeconds))
                    .build());
        }
    }

    public void leaveLobby(Player player, Lobby lobby) {
        if (!lobby.getPlayers().contains(player)) {
            player.spigot().sendMessage(new MessageBuilder().addError("You are not in a lobby").build());
            return;
        }

        if (lobby.getState() != LobbyState.WAITING) {
            player.spigot().sendMessage(new MessageBuilder().addError("You can't leave an ongoing match").build());
            return;
        }

        lobby.removePlayer(player);
        this.maybeCloseLobby(lobby);
    }

    public void handleDisconnect(Player player, Lobby lobby) {
        lobby.removePlayer(player);
        this.maybeCloseLobby(lobby);
    }

    private void maybeCloseLobby(Lobby lobby) {
        if (lobby.getPlayerCount() > 0) {
            return;
        }

        TimedTask<Lobby> task = this.getLobbyTask(lobby);
        if (task == null) {
            return;
        }

        // If all player left the lobby we clean it up so no orphan lobby stays behind
        this.plugin.getLogger().info("Lobby " + lobby.getId() + " is empty, cleanup started.");
        this.plugin.getTimer().stopTimer(task.getId());
        this.lobbies.removeIf(lobbyTask -> lobbyTask.getData().getId().equals(lobby.getId()));
    }

    public TimedTask<Lobby> getLobbyTask(Lobby lobby) {
        for (TimedTask<Lobby> task : this.lobbies) {
            if (task.getData().getId().equals(lobby.getId())) {
                return task;
            }
        }

        return null;
    }

    public List<TimedTask<Lobby>> getLobbies() {
        return this.lobbies;
    }
}
