package org.dudblockman.hostileatmosphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.dudblockman.hostileatmosphere.registry.ModAttributes;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

public class AtmosphereConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- Atmosphere ---
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
    public static final ModConfigSpec.IntValue TOXIN_RECOVERY_SECS;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_1;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_2;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_3;
    public static final ModConfigSpec.IntValue TOXIN_THRESHOLD_4;
    public static final ModConfigSpec.IntValue TOXIN_DEATH_CAP;

    // --- Protection ---
    public static final ModConfigSpec.DoubleValue UNDERWATER_AIR_DEBT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue UNDERWATER_TOXIN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue CONDUIT_PURIFICATION;
    public static final ModConfigSpec.DoubleValue CONDUIT_PURIFICATION_AIR_DEBT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CONDUIT_PURIFICATION_TOXIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue EXPEDITION_TOXIN_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("atmosphere");

        SAFE_ZONE_RECOVERY_SECS = BUILDER
                .comment("Seconds in the safe zone to fully recover from maximum air debt. Default: 30.")
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
                .comment("Toxin level at which Atmospheric Toxicity III activates. Default: 750.")
                .defineInRange("toxinThreshold3", 750, 0, 1000);

        TOXIN_THRESHOLD_4 = BUILDER
                .comment("Toxin level at which Atmospheric Toxicity IV activates. Default: 950.")
                .defineInRange("toxinThreshold4", 950, 0, 1000);

        TOXIN_DEATH_CAP = BUILDER
                .comment("Toxin level cap applied on death — if current toxin exceeds this, it is reduced to this value; if below, it is unchanged. Range 0–1000. Default: 500.")
                .defineInRange("toxinDeathCap", 500, 0, 1000);

        BUILDER.pop().push("protection");

        UNDERWATER_AIR_DEBT_MULTIPLIER = BUILDER
                .comment("Air debt accumulation rate multiplier when submerged with Water Breathing or Conduit Power. Default: 0.6.")
                .defineInRange("underwaterAirDebtMultiplier", 0.6, 0.0, 1.0);

        UNDERWATER_TOXIN_MULTIPLIER = BUILDER
                .comment("Toxin accumulation rate multiplier when submerged with Water Breathing or Conduit Power. Default: 0.6.")
                .defineInRange("underwaterToxinMultiplier", 0.6, 0.0, 1.0);

        CONDUIT_PURIFICATION = BUILDER
                .comment("If true, Conduit Power overrides the standard underwater multipliers with dedicated purification values. Default: false.")
                .define("conduitPurification", false);

        CONDUIT_PURIFICATION_AIR_DEBT_MULTIPLIER = BUILDER
                .comment("Air debt multiplier when submerged under Conduit Power and conduitPurification is enabled. Default: 0.0.")
                .defineInRange("conduitPurificationAirDebtMultiplier", 0.0, 0.0, 1.0);

        CONDUIT_PURIFICATION_TOXIN_MULTIPLIER = BUILDER
                .comment("Toxin multiplier when submerged under Conduit Power and conduitPurification is enabled. Default: 0.0.")
                .defineInRange("conduitPurificationToxinMultiplier", 0.0, 0.0, 1.0);

        EXPEDITION_TOXIN_MULTIPLIER = BUILDER
                .comment("Toxin rate multiplier for a player wearing Create's Diving Helmet + Backtank. Default: 0.5.")
                .defineInRange("expeditionToxinMultiplier", 0.5, 0.0, 1.0);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static volatile AtmosphereSettings cachedSettings;

    public static AtmosphereSettings getSettings() {
        return cachedSettings;
    }

    public static void cacheSettings() {
        cachedSettings = read();
    }

    /** Snapshot of the current config values for passing into the common engine. */
    public static AtmosphereSettings read() {
        return new AtmosphereSettings(
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
                TOXIN_RECOVERY_SECS.get(),
                TOXIN_THRESHOLD_1.get(),
                TOXIN_THRESHOLD_2.get(),
                TOXIN_THRESHOLD_3.get(),
                TOXIN_THRESHOLD_4.get(),
                TOXIN_DEATH_CAP.get(),
                UNDERWATER_AIR_DEBT_MULTIPLIER.get().floatValue(),
                UNDERWATER_TOXIN_MULTIPLIER.get().floatValue(),
                CONDUIT_PURIFICATION.get(),
                CONDUIT_PURIFICATION_AIR_DEBT_MULTIPLIER.get().floatValue(),
                CONDUIT_PURIFICATION_TOXIN_MULTIPLIER.get().floatValue(),
                EXPEDITION_TOXIN_MULTIPLIER.get().floatValue(),
                ModEffects.ATMOSPHERIC_TOXICITY,
                ModAttributes.AIR_DRAIN_RATE,
                ModAttributes.TOXIN_RATE
        );
    }
}
