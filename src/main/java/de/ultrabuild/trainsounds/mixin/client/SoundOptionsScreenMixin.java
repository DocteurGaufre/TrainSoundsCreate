package de.ultrabuild.trainsounds.mixin.client;

import de.ultrabuild.trainsounds.client.gui.TrainSoundVolumeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {

    @Inject(method = "init", at = @At("HEAD"))
    private void trainsounds$addVolumePanelButton(CallbackInfo ci) {
        SoundOptionsScreen screen = (SoundOptionsScreen) (Object) this;
        int buttonWidth = 20;
        int buttonHeight = 20;
        int x = screen.width - buttonWidth - 8;
        int y = screen.height - buttonHeight - 8;

        ((ScreenAccessor) screen).trainsounds$addRenderableWidget(Button.builder(Component.literal("🔊"), button -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client != null) {
                        client.setScreen(new TrainSoundVolumeScreen(screen));
                    }
                })
                .bounds(x, y, buttonWidth, buttonHeight)
                .build());
    }
}