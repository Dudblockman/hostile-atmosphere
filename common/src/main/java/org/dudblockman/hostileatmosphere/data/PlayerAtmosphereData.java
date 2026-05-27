package org.dudblockman.hostileatmosphere.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player atmosphere state.
 * Stored via NeoForge data attachments; serialised to NBT via {@link #CODEC}.
 *
 * airDebt            – atmosphere-consumed air ticks; effectiveCeiling = maxAir - airDebt
 * drainAccumulator   – fractional drain units pending; fire when ≥ 1; reset on zone exit
 * recoveryAccumulator– fractional recovery units pending; fire when ≥ 1; reset on zone entry
 * suffocationTicks   – continuous ticks at zero effective air; drives Miasma ramp; resets on air restoration
 * gracePeriodTicks   – immunity ticks remaining; -1 = uninitialised (first tick sets it from config)
 */
public class PlayerAtmosphereData {

    @SuppressWarnings("null") // RecordCodecBuilder unboxes boxed types; nulls cannot occur at runtime
    public static final Codec<PlayerAtmosphereData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("airDebt", 0).forGetter(d -> d.airDebt),
                    Codec.FLOAT.optionalFieldOf("drainAccumulator", 0f).forGetter(d -> d.drainAccumulator),
                    Codec.FLOAT.optionalFieldOf("recoveryAccumulator", 0f).forGetter(d -> d.recoveryAccumulator),
                    Codec.INT.optionalFieldOf("suffocationTicks", 0).forGetter(d -> d.suffocationTicks),
                    Codec.INT.optionalFieldOf("gracePeriodTicks", -1).forGetter(d -> d.gracePeriodTicks)
            ).apply(instance, PlayerAtmosphereData::new)
    );

    private int airDebt;
    private float drainAccumulator;
    private float recoveryAccumulator;
    private int suffocationTicks;
    private int gracePeriodTicks;

    public PlayerAtmosphereData() {
        this(0, 0f, 0f, 0, -1);
    }

    private PlayerAtmosphereData(int airDebt, float drainAccumulator, float recoveryAccumulator,
                                  int suffocationTicks, int gracePeriodTicks) {
        this.airDebt = airDebt;
        this.drainAccumulator = drainAccumulator;
        this.recoveryAccumulator = recoveryAccumulator;
        this.suffocationTicks = suffocationTicks;
        this.gracePeriodTicks = gracePeriodTicks;
    }

    public int getAirDebt() { return airDebt; }
    public void setAirDebt(int v) { airDebt = v; }

    public float getDrainAccumulator() { return drainAccumulator; }
    public void setDrainAccumulator(float v) { drainAccumulator = v; }

    public float getRecoveryAccumulator() { return recoveryAccumulator; }
    public void setRecoveryAccumulator(float v) { recoveryAccumulator = v; }

    public int getSuffocationTicks() { return suffocationTicks; }
    public void setSuffocationTicks(int v) { suffocationTicks = v; }

    public int getGracePeriodTicks() { return gracePeriodTicks; }
    public void setGracePeriodTicks(int v) { gracePeriodTicks = v; }

    /** True on the very first tick — grace period has never been initialised. */
    public boolean needsInit() { return gracePeriodTicks == -1; }
}
