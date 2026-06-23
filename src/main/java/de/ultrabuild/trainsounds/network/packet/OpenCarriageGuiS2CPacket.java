package de.ultrabuild.trainsounds.network.packet;

import de.ultrabuild.trainsounds.Trainsounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class OpenCarriageGuiS2CPacket implements CustomPacketPayload {

    public static final Type<OpenCarriageGuiS2CPacket> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(Trainsounds.MOD_ID, "open_carriage_gui"));
    public static final StreamCodec<FriendlyByteBuf, OpenCarriageGuiS2CPacket> STREAM_CODEC = CustomPacketPayload.codec(OpenCarriageGuiS2CPacket::write, OpenCarriageGuiS2CPacket::new);


    private final int carriageEntityId;

    public OpenCarriageGuiS2CPacket(int carriageEntityId) {
        this.carriageEntityId = carriageEntityId;
    }

    public OpenCarriageGuiS2CPacket(FriendlyByteBuf buf) {
        this.carriageEntityId = buf.readInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(carriageEntityId);
    }

    public int getCarriageEntityId() {
        return carriageEntityId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}