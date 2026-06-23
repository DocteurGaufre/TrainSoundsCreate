package de.ultrabuild.trainsounds.network.packet;

import de.ultrabuild.trainsounds.Trainsounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class ToggleCarriageEngineC2SPacket implements CustomPacketPayload {

    public static final Type<ToggleCarriageEngineC2SPacket> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(Trainsounds.MOD_ID, "toggle_engine"));
    public static final StreamCodec<FriendlyByteBuf, ToggleCarriageEngineC2SPacket> STREAM_CODEC = CustomPacketPayload.codec(ToggleCarriageEngineC2SPacket::write, ToggleCarriageEngineC2SPacket::new);

    private final int entityId;

    public ToggleCarriageEngineC2SPacket(int entityId) {
        this.entityId = entityId;
    }

    public ToggleCarriageEngineC2SPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public int getEntityId() {
        return entityId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}