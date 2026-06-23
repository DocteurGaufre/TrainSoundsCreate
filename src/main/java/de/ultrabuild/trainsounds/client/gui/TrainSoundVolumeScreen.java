package de.ultrabuild.trainsounds.client.gui;

import de.ultrabuild.trainsounds.client.config.TrainSoundVolumeConfig;
import de.ultrabuild.trainsounds.client.config.TrainSoundVolumeConfigManager;
import de.ultrabuild.trainsounds.client.gui.widget.TrainSoundVolumeSliderWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TrainSoundVolumeScreen extends Screen {

    private static final String[] CHANNELS = {"diesel", "electric", "aux"};

    private final Screen parent;

    public TrainSoundVolumeScreen(Screen parent) {
        super(Component.translatable("screen.trainsounds.volume.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TrainSoundVolumeConfig config = TrainSoundVolumeConfigManager.getConfig();

        int left = (this.width - 260) / 2;
        int top = this.height / 4;

        for (int i = 0; i < CHANNELS.length; i++) {
            String channel = CHANNELS[i];
            Component channelName = Component.translatable("gui.trainsounds.channel." + channel);
            int y = top + (i * 24);

            TrainSoundVolumeSliderWidget slider = new TrainSoundVolumeSliderWidget(
                    left,
                    y,
                    220,
                    20,
                    channel,
                    channelName,
                    config.getChannelVolume(channel),
                    (id, value) -> {
                        TrainSoundVolumeConfigManager.getConfig().setChannelVolume(id, value);
                        TrainSoundVolumeConfigManager.save();
                    }
            );
            this.addRenderableWidget(slider);

            this.addRenderableWidget(Button.builder(getMuteText(channel), button -> {
                        TrainSoundVolumeConfig cfg = TrainSoundVolumeConfigManager.getConfig();
                        cfg.setMuted(channel, !cfg.isMuted(channel));
                        TrainSoundVolumeConfigManager.save();
                        button.setMessage(getMuteText(channel));
                    })
                    .bounds(left + 226, y, 34, 20)
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds((this.width - 200) / 2, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
    }

    private Component getMuteText(String channel) {
        boolean muted = TrainSoundVolumeConfigManager.getConfig().isMuted(channel);
        if (muted) {
            return Component.literal("🔇").withStyle(net.minecraft.ChatFormatting.RED);
        }

        return Component.literal("🔊").withStyle(net.minecraft.ChatFormatting.GREEN);
    }
}