package org.dudblockman.hostileatmosphere.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.dudblockman.hostileatmosphere.Constants;

public record SyncAirDebtPayload(int airDebt) implements CustomPacketPayload {

    public static final Type<SyncAirDebtPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_air_debt"));

    public static final StreamCodec<ByteBuf, SyncAirDebtPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, SyncAirDebtPayload::airDebt, SyncAirDebtPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
