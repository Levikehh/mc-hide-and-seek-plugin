package me.levikehh.hideandseek.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import hu.nomindz.devkit.utils.MessageBuilder;
import hu.nomindz.devkit.utils.TimeFormatter;
import hu.nomindz.devkit.utils.TimedTask;
import me.levikehh.hideandseek.HideAndSeek;
import me.levikehh.hideandseek.models.Lobby;
import me.levikehh.hideandseek.models.Lobby.LobbyState;
import me.levikehh.hideandseek.models.Match.MatchState;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import me.levikehh.hideandseek.models.Match;

public class MatchManager {
    private static MatchManager instance;

    private final HideAndSeek plugin;

    private final List<TimedTask<Lobby>> lobbies;
    private final Map<String, Match> matches;

    public static MatchManager getInstance(HideAndSeek plugin) {
        if (instance == null) {
            instance = new MatchManager(plugin);
        }

        return instance;
    }

    private MatchManager(HideAndSeek plugin) {
        this.plugin = plugin;
        this.lobbies = new ArrayList<>();
        this.matches = new HashMap<>();
    }

    public void startMatch(Lobby lobby) {
        if (lobby.getMatch() != null) {
            plugin.getLogger().warning("The match for lobby " + lobby.getName() + " has already been started");
            return;
        }

        if (lobby.getPlayerCount() < 2) {
            for (Player player : lobby.getPlayers()) {
                player.spigot().sendMessage(new MessageBuilder()
                        .addError("Match could not start as there were not enough players.").build());
            }
            return;
        }

        lobby.setState(LobbyState.STARTED);

        // Create match
        Match match = new Match(
                new ArrayList<>(),
                new ArrayList<>(),
                this.plugin.config().match().hideTime(),
                this.plugin.config().match().seekTime());

        this.plugin.getTeamManager().assignTeams(match, lobby.getPlayers());

        match.setState(MatchState.HIDE_PHASE);
        lobby.setMatch(match);

        this.matches.put(match.getId(), match);

        this.plugin.getGameManager().onMatchStarted(lobby, match);

        for (Player player : match.getHiders()) {
            this.plugin.getMechanicsManager().applyHiderDisguise(player);
        }
        for (Player player : match.getSeekers()) {
            this.plugin.getMechanicsManager().lockSeeker(player);
        }

        TimedTask<Match> hideTask = this.plugin.getTimer().startTimer(
                "hide_phase_" + match.getId(),
                match,
                match.getHideTime(),
                (remainingSeconds) -> {
                    if (!this.isMatchRunning(match)) {
                        return;
                    }

                    for (Player hider : match.getHiders()) {
                        updateHiderHUD(match, remainingSeconds, hider);
                    }

                    for (Player seeker : match.getSeekers()) {
                        updateSeekerHUD(match, remainingSeconds, seeker);
                    }
                },
                () -> {
                    this.releaseSeekers(lobby, match);
                },
                false);

        match.setTask(hideTask);
    }

    private void releaseSeekers(Lobby lobby, Match match) {
        if (!this.isMatchRunning(match)) {
            return;
        }

        match.setHideElapsed(match.getTask().getElapsedSeconds());

        this.plugin.getTimer().stopTimer("hide_phase_" + match.getId());

        this.plugin.getMechanicsManager().unlockAllSeekers(match.getSeekers());
        match.setState(MatchState.SEEK_PHASE);

        TimedTask<Match> task = this.plugin.getTimer().startTimer(
                "seek_phase_" + match.getId(),
                match,
                match.getSeekTime(),
                (remainingSeconds) -> {
                    for (Player hider : match.getHiders()) {
                        updateHiderHUD(match, remainingSeconds, hider);
                    }

                    for (Player seeker : match.getSeekers()) {
                        updateSeekerHUD(match, remainingSeconds, seeker);
                    }
                },
                () -> {
                    this.endMatch(lobby, match);
                },
                false);

        match.setTask(task);
    }

    private void endMatch(Lobby lobby, Match match) {
        TimedTask<Match> seekTask = match.getTask();
        if (seekTask != null) {
            match.setSeekElapsed(seekTask.getElapsedSeconds());
        }

        int total = match.getTotalElapsed();
        int hideTime = match.getHideElapsed();
        int seekTime = match.getSeekElapsed();

        this.plugin.getMechanicsManager().cleanupMatchPlayers(lobby.getPlayers());

        lobby.setState(LobbyState.FINISHED);
        match.setState(MatchState.FINISHED);

        for (Player lobbyPlayer : lobby.getPlayers()) {
            lobbyPlayer.sendMessage(new MessageBuilder()
                    .addSuccess("Match ended. ")
                    .addVariable("Total: " + TimeFormatter.formatTimeReadable(total))
                    .addVariable(" (Hide: " + TimeFormatter.formatTimeReadable(hideTime))
                    .addVariable(", Seek: " + TimeFormatter.formatTimeReadable(seekTime) + ")")
                    .build());
            lobbyPlayer.playSound(lobbyPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        this.plugin.getTimer().stopTimer("seek_phase_" + match.getId());

        this.plugin.getGameManager().onMatchEnded(lobby, match);

        this.matches.remove(match.getId());
        this.plugin.getTeamManager().clearMatchData(match);
        lobby.setMatch(null);

        this.lobbies.removeIf(task -> task.getData().getId().equals(lobby.getId()));
    }

    private boolean isMatchRunning(Match match) {
        return match != null && match.getState() != MatchState.FINISHED;
    }

    private void updateHiderHUD(Match match, int remainingSeconds, Player player) {
        switch (match.getState()) {
            case HIDE_PHASE: {
                String timerString = TimeFormatter.formatTime(remainingSeconds);
                ChatColor color = remainingSeconds <= 3 ? ChatColor.RED : ChatColor.GRAY;
                String actionBarString = String.format(
                        color + "Hide time: %s",
                        timerString);

                player.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarString));
                break;
            }
            case SEEK_PHASE: {
                String timerString = TimeFormatter.formatTime(remainingSeconds);
                ChatColor color = remainingSeconds <= 3 ? ChatColor.GREEN : ChatColor.GRAY;
                String actionBarString = String.format(
                        color + "Seek time left: %s",
                        timerString);

                player.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarString));
                break;
            }
            default:
                break;
        }
    }

    private void updateSeekerHUD(Match match, int remainingSeconds, Player player) {
        switch (match.getState()) {
            case HIDE_PHASE: {
                String timerString = TimeFormatter.formatTime(remainingSeconds);
                ChatColor color = remainingSeconds <= 3 ? ChatColor.GOLD : ChatColor.GRAY;
                String actionBarString = String.format(
                        color + "Release in: %s",
                        timerString);

                player.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarString));
                break;
            }
            case SEEK_PHASE: {
                String timerString = TimeFormatter.formatTime(remainingSeconds);
                ChatColor color = remainingSeconds <= 3 ? ChatColor.RED : ChatColor.GRAY;
                String actionBarString = String.format(
                        color + "Seek time left: %s",
                        timerString);

                player.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarString));
                break;
            }
            default:
                break;
        }
    }

    public void handleDisconnect(Player player, Match match) {
        match.removePlayer(player);
        // TODO: if we end up clearing either team here, we should do an end-early with
        // the remaining team winning
        this.plugin.getMechanicsManager().handleLeave(player);
        this.plugin.getTeamManager().removePlayer(match, player);
    }

    public Lobby getLobbyByPlayer(Player player) {
        TimedTask<Lobby> task = this.lobbies.stream().filter(lobbyTask -> lobbyTask.getData().isParticipating(player))
                .findFirst().orElse(null);
        return task != null ? task.getData() : null;
    }
}
