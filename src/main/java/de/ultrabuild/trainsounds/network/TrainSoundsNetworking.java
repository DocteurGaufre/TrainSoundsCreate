package de.ultrabuild.trainsounds.network;

import de.ultrabuild.trainsounds.Trainsounds;
import de.ultrabuild.trainsounds.client.TrainSoundsClientHandler;
import de.ultrabuild.trainsounds.logic.EngineToggleCarrier;
import de.ultrabuild.trainsounds.network.packet.OpenCarriageGuiS2CPacket;
import de.ultrabuild.trainsounds.network.packet.ToggleCarriageEngineC2SPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class TrainSoundsNetworking {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(TrainSoundsNetworking::registerPayloads);
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(Trainsounds.MOD_ID);
        registrar.playBidirectional(ToggleCarriageEngineC2SPacket.ID, ToggleCarriageEngineC2SPacket.STREAM_CODEC, (packet, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Entity entity = player.level().getEntity(packet.getEntityId());
            if (entity instanceof EngineToggleCarrier carrier) {
                carrier.trainsounds$toggleEngineBuiltIn();
            }
        });
        registrar.playBidirectional(OpenCarriageGuiS2CPacket.ID, OpenCarriageGuiS2CPacket.STREAM_CODEC, (packet, context) -> {
            TrainSoundsClientHandler.handleOpenCarriageGuiPacket(packet);
        });
    }

    public static void sendOpenCarriageGuiPacket(ServerPlayer player, com.simibubi.create.content.trains.entity.CarriageContraptionEntity carriage) {
        PacketDistributor.sendToPlayer(player, new OpenCarriageGuiS2CPacket(carriage.getId()));
    }
}
