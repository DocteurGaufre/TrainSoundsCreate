package de.ultrabuild.trainsounds.command;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.ultrabuild.trainsounds.Trainsounds;
import de.ultrabuild.trainsounds.network.packet.TrainAnnouncePacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

// Cette annotation connecte automatiquement la classe au "Game Bus" de NeoForge
@EventBusSubscriber(modid = Trainsounds.MOD_ID)
public class TrainAnnounceCommand {

    // On écoute directement l'événement d'enregistrement des commandes
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(Commands.literal("trainannounce")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("sound", ResourceLocationArgument.id())
                        .executes(context -> {
                            ResourceLocation soundId = ResourceLocationArgument.getId(context, "sound");
                            CommandSourceStack source = context.getSource();
                            Vec3 pos = source.getPosition();

                            AABB searchBox = new AABB(pos.x - 10, pos.y - 10, pos.z - 10, pos.x + 10, pos.y + 10,
                                    pos.z + 10);
                            List<CarriageContraptionEntity> nearbyCarriages = source.getLevel()
                                    .getEntitiesOfClass(CarriageContraptionEntity.class, searchBox);

                            if (nearbyCarriages.isEmpty()) {
                                return 0;
                            }

                            CarriageContraptionEntity closestCarriage = null;
                            double minDistance = Double.MAX_VALUE;
                            for (CarriageContraptionEntity carriage : nearbyCarriages) {
                                double dist = carriage.distanceToSqr(pos);
                                if (dist < minDistance) {
                                    minDistance = dist;
                                    closestCarriage = carriage;
                                }
                            }

                            if (closestCarriage != null && closestCarriage.getCarriage() != null
                                    && closestCarriage.getCarriage().train != null) {
                                UUID trainId = closestCarriage.getCarriage().train.id;
                                PacketDistributor.sendToAllPlayers(new TrainAnnouncePacket(soundId, trainId));
                                return 1;
                            }

                            return 0;
                        })));
    }
}
