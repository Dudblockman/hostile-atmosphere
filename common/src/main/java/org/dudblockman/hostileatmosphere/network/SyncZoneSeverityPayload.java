package org.dudblockman.hostileatmosphere.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.dudblockman.hostileatmosphere.Constants;

/**
 * Syncs the data the client needs to scale particles to actual zone hazard intensity.
 *
 * <ul>
 *   <li>{@code hazardIntensity} — 0.0 = safe/approaching; in-zone = {@code leastSevereTimeSecs / thisZoneTimeSecs},
 *       so the mildest registered zone is always 1.0 and more severe zones scale proportionally from zone data</li>
 *   <li>{@code approaching}    — true when safe but within 15 blocks above the nearest zone ceiling</li>
 *   <li>{@code zoneCeilingY}   — effective Y ceiling of the active/approaching zone; {@link Integer#MAX_VALUE} if neither</li>
 *   <li>{@code zoneFloorY}     — effective Y floor of the active zone; {@link Integer#MAX_VALUE} if not in zone</li>
 * </ul>
 *
 * The client derives particle rate from {@code hazardIntensity} × a base constant, so data-pack
 * zones with custom {@code hazardTimeSecs} values automatically get appropriate particle density
 * without any hardcoded per-zone values on the client.
 */
public record SyncZoneSeverityPayload(float hazardIntensity, boolean approaching, int zoneCeilingY, int zoneFloorY)
        implements CustomPacketPayload {

    public static final Type<SyncZoneSeverityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_zone_severity"));

    public static final StreamCodec<ByteBuf, SyncZoneSeverityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SyncZoneSeverityPayload::hazardIntensity,
            ByteBufCodecs.BOOL,  SyncZoneSeverityPayload::approaching,
            ByteBufCodecs.INT,   SyncZoneSeverityPayload::zoneCeilingY,
            ByteBufCodecs.INT,   SyncZoneSeverityPayload::zoneFloorY,
            SyncZoneSeverityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
