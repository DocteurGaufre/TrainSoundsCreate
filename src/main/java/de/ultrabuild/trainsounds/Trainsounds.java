package de.ultrabuild.trainsounds;

import de.ultrabuild.trainsounds.item.EngineToggleItem;
import de.ultrabuild.trainsounds.logic.TrainEngineToggleHandler;
import de.ultrabuild.trainsounds.network.TrainSoundsNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Trainsounds.MOD_ID)
public class Trainsounds {

    public static final String MOD_ID = "trainsounds";
    
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final java.util.function.Supplier<SoundEvent> ELECTRIC_SOUND_EVENT = SOUND_EVENTS.register("electric", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "electric")));
    public static final java.util.function.Supplier<SoundEvent> DIESEL_SOUND_EVENT = SOUND_EVENTS.register("diesel", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "diesel")));
    public static final java.util.function.Supplier<SoundEvent> DEFAULT_SOUND_EVENT = SOUND_EVENTS.register("default", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "default")));
    
    public static final java.util.function.Supplier<Item> ENGINE_TOGGLE_ITEM = ITEMS.register("engine_toggle_tool", () -> new EngineToggleItem(new Item.Properties().stacksTo(1)));

    public Trainsounds(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
        ITEMS.register(modEventBus);
        
        modEventBus.addListener(this::addCreative);
        
        TrainEngineToggleHandler.register();
        TrainSoundsNetworking.register(modEventBus);
    }
    
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ENGINE_TOGGLE_ITEM.get());
        }
    }
}