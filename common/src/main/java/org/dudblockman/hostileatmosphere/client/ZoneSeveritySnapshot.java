package org.dudblockman.hostileatmosphere.client;

/**
 * Immutable snapshot of zone severity data synced from the server.
 * {@code hazardIntensity}: 0 = safe; 1 = mildest zone; higher = more severe.
 * {@code ceilingOffset}: progression delta applied to the zone ceiling Y.
 * {@code floorCeilingOffset}: progression delta applied to the next zone's ceiling (zone floor).
 */
public record ZoneSeveritySnapshot(float hazardIntensity, float ceilingOffset, float floorCeilingOffset) {
    public static final ZoneSeveritySnapshot SAFE = new ZoneSeveritySnapshot(0f, 0f, 0f);
}
