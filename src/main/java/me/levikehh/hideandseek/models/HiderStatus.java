package me.levikehh.hideandseek.models;

import java.util.UUID;

public class HiderStatus {
    public HiderForm form;
    public UUID blockDisplayId;
    public SolidState solidState;
    public long lastMovedAtMs;
    public long nextAllowedSolidifyAt;
    public float originalExperience;
    public int originalLevel;
}
