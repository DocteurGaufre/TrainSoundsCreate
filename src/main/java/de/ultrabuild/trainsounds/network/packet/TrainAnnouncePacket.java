package de.ultrabuild.trainsounds.network.packet;

import de.ultrabuild.trainsounds.Trainsounds;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record TrainAnnouncePacket(ResourceLocation soundId, UUID trainId) implements CustomPacketPayload {

    public static final Type<TrainAnnouncePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Trainsounds.MOD_ID, "train_announce"));

    public static final StreamCodec<FriendlyByteBuf, TrainAnnouncePacket> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, TrainAnnouncePacket::soundId,
            UUIDUtil.STREAM_CODEC, TrainAnnouncePacket::trainId,
            TrainAnnouncePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
