package org.dudblockman.hostileatmosphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;

public class AtmosphereConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue DANGER_Y_THRESHOLD;
    public static final ModConfigSpec.IntValue HAZARD_TIME_SECS;
    public static final ModConfigSpec.IntValue SAFE_ZONE_RECOVERY_SECS;
    public static final ModConfigSpec.IntValue GRACE_PERIOD_DAYS;

    public static final ModConfigSpec.IntValue RAMP_TIER2_SECS;
    public static final ModConfigSpec.IntValue RAMP_TIER3_SECS;
    public static final ModConfigSpec.DoubleValue RAMP_DAMAGE_TIER1;
    public static final ModConfigSpec.DoubleValue RAMP_DAMAGE_TIER2;
    public static final ModConfigSpec.DoubleValue RAMP_DAMAGE_TIER3;
    public static final ModConfigSpec.DoubleValue RAMP_INTERVAL_TIER1_SECS;
    public static final ModConfigSpec.DoubleValue RAMP_INTERVAL_TIER2_SECS;
    public static final ModConfigSpec.DoubleValue RAMP_INTERVAL_TIER3_SECS;

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
                .defineInRange("safeZoneRecoverySecs", 300, 1, 72000);

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
                RAMP_INTERVAL_TIER3_SECS.get().floatValue()
        );
    }
}
