package org.dudblockman.hostileatmosphere.network;

import org.dudblockman.hostileatmosphere.client.AtmosphereClientData;

import java.util.UUID;

/**
 * Platform-agnostic client-side handlers for server→client sync packets.
 * Each method contains only AtmosphereClientData writes — no platform API.
 * Platform-specific registration (NeoForge: ModNetworking; Fabric: future) calls these.
 */
public final class PacketHandlers {

    private PacketHandlers() {}

    public static void onAirDebt(SyncAirDebtPayload payload, UUID playerId) {
        AtmosphereClientData.setAirDebt(playerId, payload.airDebt());
    }

    public static void onToxin(SyncToxinPayload payload, UUID playerId) {
        AtmosphereClientData.setToxin(playerId, payload.toxinLevel());
        AtmosphereClientData.setMiningFatigueAmp(playerId, payload.miningFatigueAmp());
    }

    public static void onDivingActive(SyncDivingActivePayload payload, UUID playerId) {
        AtmosphereClientData.setDivingActive(playerId, payload.divingActive());
    }

    public static void onZoneSeverity(SyncZoneSeverityPayload payload, UUID playerId) {
        AtmosphereClientData.setHazardIntensity(playerId, payload.hazardIntensity());
        AtmosphereClientData.setApproachingHazard(playerId, payload.approaching());
        AtmosphereClientData.setZoneCeilingY(playerId, payload.zoneCeilingY());
        AtmosphereClientData.setZoneFloorY(playerId, payload.zoneFloorY());
    }
}
