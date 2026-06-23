package de.ultrabuild.trainsounds.client.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

public class TrainSoundVolumeSliderWidget extends AbstractSliderButton {

    private final String channel;
    private final Component channelName;
    private final BiConsumer<String, Float> onVolumeChanged;

    public TrainSoundVolumeSliderWidget(
            int x,
            int y,
            int width,
            int height,
            String channel,
            Component channelName,
            float currentVolume,
            BiConsumer<String, Float> onVolumeChanged
    ) {
        super(x, y, width, height, Component.empty(), clampNormalized(currentVolume));
        this.channel = channel;
        this.channelName = channelName;
        this.onVolumeChanged = onVolumeChanged;
        updateMessage();
    }

    public float getVolume() {
        return (float) (this.value * 2.0d);
    }

    @Override
    protected void updateMessage() {
        int percent = Math.round(getVolume() * 100.0f);
        setMessage(Component.translatable("gui.trainsounds.volume_entry", channelName, percent));
    }

    @Override
    protected void applyValue() {
        onVolumeChanged.accept(channel, getVolume());
        updateMessage();
    }

    private static double clampNormalized(float volume) {
        return Math.max(0.0d, Math.min(1.0d, volume / 2.0f));
    }
}