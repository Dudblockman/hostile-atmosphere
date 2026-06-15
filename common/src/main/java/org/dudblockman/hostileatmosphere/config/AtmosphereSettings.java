package org.dudblockman.hostileatmosphere.config;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.dudblockman.hostileatmosphere.registry.ModEntityTags;

import java.util.function.Supplier;

public record AtmosphereSettings(
        // Air depletion
        int safeZoneRecoverySecs,
        int gracePeriodDays,
        // Miasma damage ramp
        int rampTier2Secs,
        int rampTier3Secs,
        float rampDamageTier1,
        float rampDamageTier2,
        float rampDamageTier3,
        float rampIntervalTier1Secs,
        float rampIntervalTier2Secs,
        float rampIntervalTier3Secs,
        // Toxin
        int toxinRecoverySecs,
        int toxinThreshold1,
        int toxinThreshold2,
        int toxinThreshold3,
        int toxinThreshold4,
        int toxinDeathCap,
        boolean rainToxinMultiplierEnabled,
        float rainToxinMultiplier,
        // Protection
        float underwaterAirDebtMultiplier,
        float underwaterToxinMultiplier,
        boolean conduitPurification,
        float conduitPurificationAirDebtMultiplier,
        float conduitPurificationToxinMultiplier,
        float expeditionToxinMultiplier,
        Holder<MobEffect> toxicityEffect,
        Holder<Attribute> airDrainRate,
        Holder<Attribute> toxinRate
) {
    public record EntityHazardSettings(
            boolean enabled,
            boolean damagePassive,
            boolean damageHostile,
            boolean damageAquatic,
            boolean damageNpc,
            boolean suppressPassive,
            boolean suppressHostile,
            boolean suppressAquatic,
            boolean suppressNpc
    ) {
        public boolean isDamageEnabled(EntityType<?> type) {
            if (!enabled()) return false;
            return (damagePassive()  && type.is(ModEntityTags.HAZARD_DAMAGE_PASSIVE))
                || (damageHostile()  && type.is(ModEntityTags.HAZARD_DAMAGE_HOSTILE))
                || (damageAquatic()  && type.is(ModEntityTags.HAZARD_DAMAGE_AQUATIC))
                || (damageNpc()      && type.is(ModEntityTags.HAZARD_DAMAGE_NPC));
        }

        public boolean isSuppressEnabled(EntityType<?> type) {
            if (!enabled()) return false;
            return (suppressPassive()  && type.is(ModEntityTags.SPAWN_SUPPRESS_PASSIVE))
                || (suppressHostile()  && type.is(ModEntityTags.SPAWN_SUPPRESS_HOSTILE))
                || (suppressAquatic()  && type.is(ModEntityTags.SPAWN_SUPPRESS_AQUATIC))
                || (suppressNpc()      && type.is(ModEntityTags.SPAWN_SUPPRESS_NPC));
        }
    }

    /** Default settings mirroring the shipped config file values, for use in tests. */
    public static AtmosphereSettings defaults() {
        return new AtmosphereSettings(
                30, 3, 10, 30, 1f, 2f, 4f, 1f, 0.75f, 0.5f,
                14400, 250, 500, 750, 950, 500,
                false, 1.5f,
                0.6f, 0.6f, false, 0f, 0f, 0.5f,
                null, null, null);
    }

    // ---- Config gateway (merged from ConfigAccess) ------------------------------------------

    private static volatile Supplier<AtmosphereSettings>         atmosphereSupplier;
    private static volatile Supplier<EntityHazardSettings>       entityHazardSupplier;

    /**
     * Registers platform-specific config suppliers. Called once at mod init before any common
     * code can execute. Throws if called more than once to catch double-registration bugs.
     */
    public static void register(Supplier<AtmosphereSettings> atmosphere,
                                Supplier<EntityHazardSettings> entityHazard) {
        if (atmosphereSupplier != null) throw new IllegalStateException("AtmosphereSettings already registered");
        atmosphereSupplier   = atmosphere;
        entityHazardSupplier = entityHazard;
    }

    /** Returns the current atmosphere settings snapshot, or {@code null} before config loads. */
    public static AtmosphereSettings getSettings() {
        return atmosphereSupplier != null ? atmosphereSupplier.get() : null;
    }

    /** Returns the current entity-hazard settings snapshot, or {@code null} before config loads. */
    public static EntityHazardSettings getEntityHazardSettings() {
        return entityHazardSupplier != null ? entityHazardSupplier.get() : null;
    }

    public static boolean isDamageEnabled(EntityType<?> type) {
        EntityHazardSettings cfg = getEntityHazardSettings();
        return cfg != null && cfg.isDamageEnabled(type);
    }
}
