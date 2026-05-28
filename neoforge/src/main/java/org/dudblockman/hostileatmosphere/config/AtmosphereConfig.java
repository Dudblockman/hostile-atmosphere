package org.dudblockman.hostileatmosphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

public class AtmosphereConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- Atmosphere ---
    public static final ModConfigSpec.IntValue DANGER_Y_THRESHOLD;
    public static final ModConfigSpec.IntValue HAZARD_TIME_SECS;
    public static final ModConfigSpec.IntValue SAFE_ZONE_RECOVERY_SECS;
    public static final ModConfigSpec.IntValue GRACE_PERIOD_DAYS;

    // --- Miasma damage ramp ---
    public static final ModConfigSpec.IntValue RAMP_TIER2_SECS;
    public static final ModConfigSpec.IntValue RAMP_TIER3_SECS;
    public static final ModConfigSpec.DoubleValue RAMP_DAMAGE_TIER1;
    public static final ModConfigSpec.DoubleValue RAMP_DAMAGE_TIER2;
    public static final ModConfigSpec.DoubleValue RAMP_DAMAGE_TIER3;
    public static final ModConfigSpec.DoubleValue RAMP_INTERVAL_TIER1_SECS;
    public static final ModConfigSpec.DoubleValue RAMP_INTERVAL_TIER2_SECS;
    public static final ModConfigSpec.DoubleValue RAMP_INTERVAL_TIER3_SECS;

    // --- Toxin buildup ---
    public static final ModConfigSpec.IntValue TOXIN_BUILDUP_SECS;
    public static final ModConfigSpec.IntValue TOXIN_RECOVERY_SECS;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_1;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_2;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_3;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_4;
    public static final ModConfigSpec.DoubleValue TOXIN_RETAIN_ON_DEATH;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("atmosphere");

        DANGER_Y_THRESHOLD = BUILDER
                .comment("Y level at or below which the atmosphere is dangerous.")
                .defineInRange("dangerYThreshold", 96, -64, 320);

        HAZARD_TIME_SECS = BUILDER
                .comment("Seconds of unprotected exposure before air is fully depleted. Default: 480 (8 min).")
                .defineInRange("hazardTimeSecs", 480, 1, 72000);

        SAFE_ZONE_RECOVERY_SECS = BUILDER
                .comment("Seconds in the safe zone to fully recover from maximum air debt. Default: 300 (5 min).")
                .defineInRange("safeZoneRecoverySecs", 30, 1, 72000);

        GRACE_PERIOD_DAYS = BUILDER
                .comment("In-game days of immunity for new players (0 = disabled).")
                .defineInRange("gracePeriodDays", 3, 0, 30);

        BUILDER.pop().push("miasmaRamp");

        RAMP_TIER2_SECS = BUILDER
                .comment("Seconds at zero air before damage escalates to tier 2. Default: 10.")
                .defineInRange("rampTier2Secs", 10, 0, 300);

        RAMP_TIER3_SECS = BUILDER
                .comment("Seconds at zero air before damage escalates to tier 3. Default: 30.")
                .defineInRange("rampTier3Secs", 30, 0, 300);

        RAMP_DAMAGE_TIER1 = BUILDER
                .comment("Half-hearts per hit at tier 1.")
                .defineInRange("rampDamageTier1", 1.0, 0.0, 20.0);

        RAMP_DAMAGE_TIER2 = BUILDER
                .comment("Half-hearts per hit at tier 2.")
                .defineInRange("rampDamageTier2", 2.0, 0.0, 20.0);

        RAMP_DAMAGE_TIER3 = BUILDER
                .comment("Half-hearts per hit at tier 3.")
                .defineInRange("rampDamageTier3", 4.0, 0.0, 20.0);

        RAMP_INTERVAL_TIER1_SECS = BUILDER
                .comment("Seconds between Miasma hits at tier 1. Default: 1.0.")
                .defineInRange("rampIntervalTier1Secs", 1.0, 0.05, 10.0);

        RAMP_INTERVAL_TIER2_SECS = BUILDER
                .comment("Seconds between Miasma hits at tier 2. Default: 0.75.")
                .defineInRange("rampIntervalTier2Secs", 0.75, 0.05, 10.0);

        RAMP_INTERVAL_TIER3_SECS = BUILDER
                .comment("Seconds between Miasma hits at tier 3. Default: 0.5.")
                .defineInRange("rampIntervalTier3Secs", 0.5, 0.05, 10.0);

        BUILDER.pop().push("toxin");

        TOXIN_BUILDUP_SECS = BUILDER
                .comment("Seconds of continuous hazard-zone exposure to go from 0 to maximum toxin (1000). Default: 2400 (40 min).")
                .defineInRange("toxinBuildupSecs", 2400, 1, 864000);

        TOXIN_RECOVERY_SECS = BUILDER
                .comment("Seconds in the safe zone to clear from maximum toxin (1000) to zero. Default: 14400 (240 min).")
                .defineInRange("toxinRecoverySecs", 14400, 1, 864000);

        TOXIN_THRESHOLD_1 = BUILDER
                .comment("Toxin level at which Atmospheric Toxicity I activates (Weakness). Default: 250.")
                .defineInRange("toxinThreshold1", 250, 0, 1000);

        TOXIN_THRESHOLD_2 = BUILDER
                .comment("Toxin level at which Atmospheric Toxicity II activates (Weakness + Mining Fatigue). Default: 500.")
                .defineInRange("toxinThreshold2", 500, 0, 1000);

        TOXIN_THRESHOLD_3 = BUILDER
                .comment("Toxin level at which Atmospheric Toxicity III activates (above + Poison-style damage). Default: 750.")
                .defineInRange("toxinThreshold3", 750, 0, 1000);

        TOXIN_THRESHOLD_4 = BUILDER
                .comment("Toxin level at which Atmospheric Toxicity IV activates (above + Wither-style damage). Default: 950.")
                .defineInRange("toxinThreshold4", 950, 0, 1000);

        TOXIN_RETAIN_ON_DEATH = BUILDER
                .comment("Fraction of toxin level carried through death (0.0 = full reset on death, 1.0 = full carry-over). Default: 0.5.")
                .defineInRange("toxinRetainOnDeath", 0.5, 0.0, 1.0);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /** Snapshot of the current config values for passing into the common engine. */
    public static AtmosphereSettings read() {
        return new AtmosphereSettings(
                DANGER_Y_THRESHOLD.get(),
                HAZARD_TIME_SECS.get(),
                SAFE_ZONE_RECOVERY_SECS.get(),
                GRACE_PERIOD_DAYS.get(),
                RAMP_TIER2_SECS.get(),
                RAMP_TIER3_SECS.get(),
                RAMP_DAMAGE_TIER1.get().floatValue(),
                RAMP_DAMAGE_TIER2.get().floatValue(),
                RAMP_DAMAGE_TIER3.get().floatValue(),
                RAMP_INTERVAL_TIER1_SECS.get().floatValue(),
                RAMP_INTERVAL_TIER2_SECS.get().floatValue(),
                RAMP_INTERVAL_TIER3_SECS.get().floatValue(),
                TOXIN_BUILDUP_SECS.get(),
                TOXIN_RECOVERY_SECS.get(),
                TOXIN_THRESHOLD_1.get(),
                TOXIN_THRESHOLD_2.get(),
                TOXIN_THRESHOLD_3.get(),
                TOXIN_THRESHOLD_4.get(),
                TOXIN_RETAIN_ON_DEATH.get().floatValue(),
                ModEffects.ATMOSPHERIC_TOXICITY
        );
    }
}
