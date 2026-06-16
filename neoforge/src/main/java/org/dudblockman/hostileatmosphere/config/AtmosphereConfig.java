package org.dudblockman.hostileatmosphere.config;

import net.minecraft.world.entity.EntityType;
import org.dudblockman.hostileatmosphere.registry.ModAttributes;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

public final class AtmosphereConfig {

    private AtmosphereConfig() {}

    private static volatile AtmosphereSettings settings;
    private static volatile AtmosphereSettings.EntityHazardSettings entityHazardSettings;

    public static AtmosphereSettings getSettings() { return settings; }

    public static AtmosphereSettings.EntityHazardSettings getEntityHazardSettings() { return entityHazardSettings; }

    public static boolean isDamageEnabled(EntityType<?> type) {
        AtmosphereSettings.EntityHazardSettings cfg = getEntityHazardSettings();
        return cfg != null && cfg.isDamageEnabled(type);
    }

    public static void cacheSettings() {
        settings = read();
        entityHazardSettings = readEntityHazard();
    }

    private static AtmosphereSettings.EntityHazardSettings readEntityHazard() {
        return new AtmosphereSettings.EntityHazardSettings(
                AtmosphereConfigSpec.ENTITY_HAZARD_ENABLED.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_DAMAGE_PASSIVE.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_DAMAGE_HOSTILE.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_DAMAGE_AQUATIC.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_DAMAGE_NPC.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_SUPPRESS_PASSIVE.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_SUPPRESS_HOSTILE.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_SUPPRESS_AQUATIC.get().booleanValue(),
                AtmosphereConfigSpec.ENTITY_SUPPRESS_NPC.get().booleanValue()
        );
    }

    private static AtmosphereSettings read() {
        return new AtmosphereSettings(
                AtmosphereConfigSpec.SAFE_ZONE_RECOVERY_SECS.get(),
                AtmosphereConfigSpec.GRACE_PERIOD_DAYS.get(),
                AtmosphereConfigSpec.RAMP_TIER2_SECS.get(),
                AtmosphereConfigSpec.RAMP_TIER3_SECS.get(),
                AtmosphereConfigSpec.RAMP_DAMAGE_TIER1.get().floatValue(),
                AtmosphereConfigSpec.RAMP_DAMAGE_TIER2.get().floatValue(),
                AtmosphereConfigSpec.RAMP_DAMAGE_TIER3.get().floatValue(),
                AtmosphereConfigSpec.RAMP_INTERVAL_TIER1_SECS.get().floatValue(),
                AtmosphereConfigSpec.RAMP_INTERVAL_TIER2_SECS.get().floatValue(),
                AtmosphereConfigSpec.RAMP_INTERVAL_TIER3_SECS.get().floatValue(),
                AtmosphereConfigSpec.TOXIN_RECOVERY_SECS.get(),
                AtmosphereConfigSpec.TOXIN_THRESHOLD_1.get(),
                AtmosphereConfigSpec.TOXIN_THRESHOLD_2.get(),
                AtmosphereConfigSpec.TOXIN_THRESHOLD_3.get(),
                AtmosphereConfigSpec.TOXIN_THRESHOLD_4.get(),
                AtmosphereConfigSpec.TOXIN_DEATH_CAP.get(),
                AtmosphereConfigSpec.RAIN_TOXIN_MULTIPLIER_ENABLED.get(),
                AtmosphereConfigSpec.RAIN_TOXIN_MULTIPLIER.get().floatValue(),
                AtmosphereConfigSpec.UNDERWATER_AIR_DEBT_MULTIPLIER.get().floatValue(),
                AtmosphereConfigSpec.UNDERWATER_TOXIN_MULTIPLIER.get().floatValue(),
                AtmosphereConfigSpec.CONDUIT_PURIFICATION.get(),
                AtmosphereConfigSpec.CONDUIT_PURIFICATION_AIR_DEBT_MULTIPLIER.get().floatValue(),
                AtmosphereConfigSpec.CONDUIT_PURIFICATION_TOXIN_MULTIPLIER.get().floatValue(),
                AtmosphereConfigSpec.EXPEDITION_TOXIN_MULTIPLIER.get().floatValue(),
                ModEffects.ATMOSPHERIC_TOXICITY,
                ModAttributes.AIR_DRAIN_RATE,
                ModAttributes.TOXIN_RATE
        );
    }
}
