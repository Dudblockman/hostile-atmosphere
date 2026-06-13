package org.dudblockman.hostileatmosphere.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.dudblockman.hostileatmosphere.Constants;

/**
 /**
 * Syncs zone hazard intensity to the client for particle scaling.
 * {@code hazardIntensity}: 0.0 when safe; in-zone = {@code leastSevereTimeSecs / thisZoneTimeSecs},
 * so the mildest registered zone is always 1.0 and more severe zones scale proportionally.
 */
public record SyncZoneSeverityPayload(float hazardIntensity)
        implements CustomPacketPayload {

    public static final Type<SyncZoneSeverityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_zone_severity"));

    public static final StreamCodec<ByteBuf, SyncZoneSeverityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SyncZoneSeverityPayload::hazardIntensity,
            SyncZoneSeverityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
