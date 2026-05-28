package org.dudblockman.hostileatmosphere.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.dudblockman.hostileatmosphere.Constants;

public record SyncDivingActivePayload(boolean divingActive) implements CustomPacketPayload {

    public static final Type<SyncDivingActivePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_diving_active"));

    public static final StreamCodec<ByteBuf, SyncDivingActivePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, SyncDivingActivePayload::divingActive, SyncDivingActivePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
