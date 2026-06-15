package org.dudblockman.hostileatmosphere.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.dudblockman.hostileatmosphere.Constants;

/**
 * Syncs zone hazard intensity and ceiling offsets to the client for particle scaling.
 * {@code hazardIntensity}: 0.0 when safe; in-zone = {@code leastSevereTimeSecs / thisZoneTimeSecs}.
 * {@code ceilingOffset}: effective ceiling minus base ceiling at the player's position (0 when safe).
 * {@code floorCeilingOffset}: effective floor-zone ceiling minus its base ceiling (0 when safe or no floor zone).
 */
public record SyncZoneSeverityPayload(float hazardIntensity, float ceilingOffset, float floorCeilingOffset)
        implements CustomPacketPayload {

    public static final Type<SyncZoneSeverityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_zone_severity"));

    public static final StreamCodec<ByteBuf, SyncZoneSeverityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SyncZoneSeverityPayload::hazardIntensity,
            ByteBufCodecs.FLOAT, SyncZoneSeverityPayload::ceilingOffset,
            ByteBufCodecs.FLOAT, SyncZoneSeverityPayload::floorCeilingOffset,
            SyncZoneSeverityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
