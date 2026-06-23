package de.ultrabuild.trainsounds.client.config;

import net.minecraft.util.Mth;

public class TrainSoundVolumeConfig {

    public float dieselVolume = 1.0f;
    public boolean dieselMuted = false;

    public float electricVolume = 1.0f;
    public boolean electricMuted = false;

    public float auxVolume = 1.0f;
    public boolean auxMuted = false;

    public void clamp() {
        dieselVolume = Mth.clamp(dieselVolume, 0.0f, 2.0f);
        electricVolume = Mth.clamp(electricVolume, 0.0f, 2.0f);
        auxVolume = Mth.clamp(auxVolume, 0.0f, 2.0f);
    }

    public float getVolumeMultiplier(String channel) {
        return switch (channel) {
            case "diesel" -> dieselMuted ? 0.0f : dieselVolume;
            case "electric" -> electricMuted ? 0.0f : electricVolume;
            case "aux" -> auxMuted ? 0.0f : auxVolume;
            default -> 1.0f;
        };
    }

    public float getChannelVolume(String channel) {
        return switch (channel) {
            case "diesel" -> dieselVolume;
            case "electric" -> electricVolume;
            case "aux" -> auxVolume;
            default -> 1.0f;
        };
    }

    public boolean isMuted(String channel) {
        return switch (channel) {
            case "diesel" -> dieselMuted;
            case "electric" -> electricMuted;
            case "aux" -> auxMuted;
            default -> false;
        };
    }

    public void setChannelVolume(String channel, float volume) {
        float clamped = Mth.clamp(volume, 0.0f, 2.0f);
        switch (channel) {
            case "diesel" -> dieselVolume = clamped;
            case "electric" -> electricVolume = clamped;
            case "aux" -> auxVolume = clamped;
            default -> {
            }
        }
    }

    public void setMuted(String channel, boolean muted) {
        switch (channel) {
            case "diesel" -> dieselMuted = muted;
            case "electric" -> electricMuted = muted;
            case "aux" -> auxMuted = muted;
            default -> {
            }
        }
    }
}