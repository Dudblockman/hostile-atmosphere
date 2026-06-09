package org.dudblockman.hostileatmosphere.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CeilingGridDebug {

    /** Players with ceiling-grid debug particles enabled; value = radius in blocks. */
    private static final Map<UUID, Integer> particleGridRadii = new ConcurrentHashMap<>();

    private CeilingGridDebug() {}

    /** Enables (radius > 0) or disables (radius == 0) the ceiling particle grid for the source player. */
    public static int toggleGrid(CommandSourceStack src, int radius) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[HA] Ceiling grid requires a player source."));
            return 0;
        }
        if (radius <= 0) {
            particleGridRadii.remove(player.getUUID());
            src.sendSuccess(() -> Component.literal("[HA] Ceiling particle grid disabled."), false);
            return 0;
        }
        particleGridRadii.put(player.getUUID(), radius);
        src.sendSuccess(() -> Component.literal("[HA] Ceiling particle grid enabled (radius " + radius + "). Use radius 0 to disable."), false);
        return radius;
    }

    public static void renderForPlayer(ServerPlayer player, List<ZoneDefinition> zones,
            List<String> ids, long tick, double px, double pz,
            AtmosphereProgressionData progression) {
        Integer radius = particleGridRadii.get(player.getUUID());
        if (radius == null || tick % 10 != 0) return;
        renderGrid(player, zones, ids, tick, px, pz, progression, radius);
    }

    public static void onPlayerLoggedOut(UUID playerId) {
        particleGridRadii.remove(playerId);
    }

    private static void renderGrid(ServerPlayer player, List<ZoneDefinition> zones,
            List<String> ids, long tick, double px, double pz,
            AtmosphereProgressionData progression, int radius) {
        ServerLevel level = (ServerLevel) player.level();
        int cx = (int) Math.floor(px);
        int cz = (int) Math.floor(pz);
        int n = zones.size();
        if (n == 0) return;

        for (int i = 0; i < n; i++) {
            // Evenly-spaced hue across the colour wheel, full saturation & brightness.
            int packed = hsbToRgb((float) i / n);
            float r = ((packed >> 16) & 0xFF) / 255.0f;
            float g = ((packed >>  8) & 0xFF) / 255.0f;
            float b = ( packed        & 0xFF) / 255.0f;
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.0f);

            String zoneId = ids.get(i);
            ZoneDefinition zone = zones.get(i);

            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    double wx = cx + dx + 0.5;
                    double wz = cz + dz + 0.5;
                    double base    = zone.evalCeiling(tick, wx, wz);
                    double ceiling = progression.getEffectiveCeiling(tick, wx, wz, zoneId, base);
                    level.sendParticles(player, dust, false, wx, ceiling, wz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /** Pure HSB→RGB for hue∈[0,1], s=1, b=1 — avoids java.awt.Color in server-side code. */
    private static int hsbToRgb(float hue) {
        int h = (int) (hue * 6);
        float f = hue * 6 - h;
        float q = 1 - f, t = f;
        float rv, gv, bv;
        switch (h % 6) {
            case 0 -> { rv = 1; gv = t; bv = 0; }
            case 1 -> { rv = q; gv = 1; bv = 0; }
            case 2 -> { rv = 0; gv = 1; bv = t; }
            case 3 -> { rv = 0; gv = q; bv = 1; }
            case 4 -> { rv = t; gv = 0; bv = 1; }
            default -> { rv = 1; gv = 0; bv = q; }
        }
        return ((int)(rv * 255) << 16) | ((int)(gv * 255) << 8) | (int)(bv * 255);
    }
}
