package me.levikehh.hideandseek.models;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import hu.nomindz.devkit.utils.TimedTask;

public class Match {
    private final String id;
    private final long startTime;
    private final int durationSeconds;
    private final int hidePhaseSeconds;
    private final int seekPhaseSeconds;

    private List<Player> hiders;
    private List<Player> seekers;
    private TimedTask<Match> task;
    private int hideElapsedSec = 0;
    private int seekElapsedSec = 0;
    private MatchState state;

    public enum MatchState {
        STARTING, // countdown before the match begins
        HIDE_PHASE, // the hiders get some time to hide before seekers released
        SEEK_PHASE, // the seekers are searching for the hiders
        FINISHED, // either team won
    }

    public Match(List<Player> hiders, List<Player> seekers, int hidePhaseSeconds, int seekPhaseSeconds) {
        this.id = "match_" + UUID.randomUUID().toString();
        this.hiders = hiders;
        this.seekers = seekers;
        this.startTime = System.currentTimeMillis();
        this.durationSeconds = hidePhaseSeconds + seekPhaseSeconds;
        this.hidePhaseSeconds = hidePhaseSeconds;
        this.seekPhaseSeconds = seekPhaseSeconds;
        this.state = MatchState.STARTING;
    }

    public String getId() {
        return this.id;
    }

    public MatchState getState() {
        return this.state;
    }

    public void setState(MatchState newState) {
        this.state = newState;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public int getDuration() {
        return this.durationSeconds;
    }

    public int getHideTime() {
        return this.hidePhaseSeconds;
    }

    public int getSeekTime() {
        return this.seekPhaseSeconds;
    }

    public void setHiders(List<Player> hiders) {
        this.hiders = hiders;
    }

    public List<Player> getHiders() {
        return this.hiders;
    }

    public void setSeekers(List<Player> seekers) {
        this.seekers = seekers;
    }

    public void addSeeker(Player seeker) {
        this.seekers.add(seeker);
    }

    public List<Player> getSeekers() {
        return this.seekers;
    }

    public void setHideElapsed(int s) {
        this.hideElapsedSec = s;
    }

    public void setSeekElapsed(int s) {
        this.seekElapsedSec = s;
    }

    public int getHideElapsed() {
        return hideElapsedSec;
    }

    public int getSeekElapsed() {
        return seekElapsedSec;
    }

    public int getTotalElapsed() {
        return hideElapsedSec + seekElapsedSec;
    }

    public void setTask(TimedTask<Match> task) {
        this.task = task;
    }

    public TimedTask<Match> getTask() {
        return this.task;
    }

    public boolean isParticipant(Player player) {
        if (this.hiders.contains(player)) {
            return true;
        }

        if (this.seekers.contains(player)) {
            return true;
        }

        return false;
    }

    public boolean isParticipant(UUID playerId) {
        for (Player hider : this.hiders) {
            if (hider.getUniqueId().equals(playerId)) {
                return true;
            }
        }

        for (Player seeker : this.seekers) {
            if (seeker.getUniqueId().equals(playerId)) {
                return true;
            }
        }

        return false;
    }

    public boolean isSeeker(Player player) {
        return this.seekers.contains(player);
    }

    public boolean isSeeker(UUID playerId) {
        for (Player seeker : this.seekers) {
            if (seeker.getUniqueId().equals(playerId)) {
                return true;
            }
        }

        return false;
    }

    public boolean isHider(Player player) {
        return this.hiders.contains(player);
    }

    public boolean isHider(UUID playerId) {
        for (Player hider : this.hiders) {
            if (hider.getUniqueId().equals(playerId)) {
                return true;
            }
        }

        return false;
    }

    public void removePlayer(Player player) {
        if (!this.hiders.remove(player)) {
            this.seekers.remove(player);
        }
    }
}
