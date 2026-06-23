package de.ultrabuild.trainsounds.client;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.ultrabuild.trainsounds.Trainsounds;
import de.ultrabuild.trainsounds.client.gui.CarriageManagementScreen;
import de.ultrabuild.trainsounds.network.TrainSoundsNetworking;
import de.ultrabuild.trainsounds.network.packet.OpenCarriageGuiS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = Trainsounds.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class TrainSoundsClientHandler {

    public static void register() {
        // Registering is handled by EventBusSubscriber
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            ItemStack stack = event.getEntity().getItemInHand(event.getHand());

            if (!stack.is(Trainsounds.ENGINE_TOGGLE_ITEM.get())) {
                return;
            }

            if (!(event.getTarget() instanceof CarriageContraptionEntity carriageEntity)) {
                return;
            }

            openCarriageManagementScreen(carriageEntity);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static void handleOpenCarriageGuiPacket(OpenCarriageGuiS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.level != null) {
                Entity entity = client.level.getEntity(packet.getCarriageEntityId());
                if (entity instanceof CarriageContraptionEntity carriage) {
                    openCarriageManagementScreen(carriage);
                }
            }
        });
    }

    public static void openCarriageManagementScreen(CarriageContraptionEntity startCarriage) {
        Minecraft client = Minecraft.getInstance();
        Level world = startCarriage.level();

        if (world == null) {
            return;
        }

        List<CarriageContraptionEntity> allCarriages = getAllCarriagesInTrain(startCarriage, world);

        if (allCarriages.isEmpty()) {
            return;
        }

        int startIndex = 0;
        for (int i = 0; i < allCarriages.size(); i++) {
            if (allCarriages.get(i).getId() == startCarriage.getId()) {
                startIndex = i;
                break;
            }
        }

        client.setScreen(new CarriageManagementScreen(client.screen, allCarriages, startIndex));
    }

    public static List<CarriageContraptionEntity> getAllCarriagesInTrain(CarriageContraptionEntity startCarriage, Level world) {
        List<CarriageContraptionEntity> result = new ArrayList<>();

        com.simibubi.create.content.trains.entity.Carriage carriage = startCarriage.getCarriage();
        if (carriage == null || carriage.train == null) {
            result.add(startCarriage);
            return result;
        }

        List<CarriageContraptionEntity> candidates = world.getEntitiesOfClass(
                CarriageContraptionEntity.class,
                startCarriage.getBoundingBox().inflate(512.0d),
                e -> e != null && e.isAlive() && e.getCarriage() != null && e.getCarriage().train == carriage.train
        );

        candidates.sort(Comparator.comparingInt(c -> c.carriageIndex));
        result.addAll(candidates);

        return result;
    }
}
