package de.ultrabuild.trainsounds.client;

import de.ultrabuild.trainsounds.Trainsounds;
import de.ultrabuild.trainsounds.client.config.TrainSoundVolumeConfigManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Trainsounds.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TrainsoundsClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TrainSoundVolumeConfigManager.ensureLoaded();
        TrainSoundsClientHandler.register();
    }
}