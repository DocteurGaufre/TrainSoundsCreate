package de.ultrabuild.trainsounds.client;

import de.ultrabuild.trainsounds.Trainsounds;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = Trainsounds.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class TrainsoundsDataGenerator {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        // Pack creation and datagen registration go here
    }
}