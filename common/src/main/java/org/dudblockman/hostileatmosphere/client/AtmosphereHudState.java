package org.dudblockman.hostileatmosphere.client;

/** Client-side HUD animation state not tied to per-player data. */
public final class AtmosphereHudState {

    private static volatile boolean forceHeartWiggle;

    private AtmosphereHudState() {}

    public static boolean isForceHeartWiggle() { return forceHeartWiggle; }
    public static void setForceHeartWiggle(boolean v) { forceHeartWiggle = v; }
}
