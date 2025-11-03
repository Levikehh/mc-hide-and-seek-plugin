package me.levikehh.hideandseek.config;

import hu.nomindz.devkit.config.DatabaseConfig;
import jakarta.validation.constraints.*;

public record HideAndSeekConfig(
        @Min(1) int config_version,
        DatabaseConfig database,
        Lobby lobby,
        Match match,
        Team teams) {
    public record Lobby(
            @Min(10) int duration) {
    }

    public record Match(
            @Min(30) int hideTime,
            @Min(60) int seekTime,
            @Min(1) int maxSeeker) {
    }

    public record Team(
            Hider hiders,
            Seeker seekers) {
        public record Hider(
                @Min(32) float viewRange,
                @Min(1) float solidifyCooldown,
                @Min(2) float timeToSolid,
                @Min(3) int health) {
        }

        public record Seeker() {
        }
    }
}
