package org.dudblockman.hostileatmosphere.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.dudblockman.hostileatmosphere.Constants;

public record SyncToxinPayload(int toxinLevel) implements CustomPacketPayload {

    public static final Type<SyncToxinPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_toxin"));

    public static final StreamCodec<ByteBuf, SyncToxinPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, SyncToxinPayload::toxinLevel, SyncToxinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
