package org.dudblockman.hostileatmosphere.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes a scalar value from the current game tick and optional world position.
 * {@link Constant} supports a linear tween from {@code fromValue} to {@code value} over
 * {@code tweenTicks} ticks. Dynamic sources (sin, perlin) have an amplitude ramp-in from 0.
 * {@code tweenTicks=0} means instant in all cases.
 */
public interface ValueSource {

    double get(long tick);

    default double get(long tick, double x, double z) { return get(tick); }

    String type();

    /** Returns a settled copy with tweenTicks=0 once the tween is complete, or {@code this} if still in progress. */
    default ValueSource settle(long tick) { return this; }

    @SuppressWarnings({"null", "unchecked"})
    Map<String, MapCodec<? extends ValueSource>> BY_TYPE = Map.of(
            "constant", Constant.CODEC,
            "sin",      SinWave.CODEC,
            "perlin",   Perlin.CODEC
    );

    Codec<ValueSource> CODEC = Codec.STRING.dispatch("type", ValueSource::type, BY_TYPE::get);

    // ------------------------------------------------------------------------------------------

    record Constant(double fromValue, double value, long tweenTicks, long startTick) implements ValueSource {

        @Override public String type() { return "constant"; }

        @Override
        public double get(long tick) {
            if (tweenTicks <= 0) return value;
            double t = Mth.clamp((double) (tick - startTick) / tweenTicks, 0.0, 1.0);
            return fromValue + (value - fromValue) * t;
        }

        @Override
        public ValueSource settle(long tick) {
            return tweenTicks > 0 && tick - startTick >= tweenTicks
                    ? new Constant(0.0, value, 0L, 0L) : this;
        }

        @SuppressWarnings("null")
        static final MapCodec<Constant> CODEC = RecordCodecBuilder.<Constant>mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("fromValue", 0.0).forGetter(Constant::fromValue),
                Codec.DOUBLE.fieldOf("value").forGetter(Constant::value),
                Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(Constant::tweenTicks),
                Codec.LONG.optionalFieldOf("startTick", 0L).forGetter(Constant::startTick)
        ).apply(i, Constant::new));
    }

    record SinWave(
            double fromAmplitude, double amplitude,
            double fromPeriodTicks, double periodTicks,
            double fromPhaseTicks, double phaseTicks,
            long tweenTicks, long startTick
    ) implements ValueSource {

        @Override public String type() { return "sin"; }

        @Override
        public double get(long tick) {
            double t   = tweenTicks <= 0 ? 1.0
                    : Mth.clamp((double) (tick - startTick) / tweenTicks, 0.0, 1.0);
            double amp = fromAmplitude + (amplitude - fromAmplitude) * t;
            // fromPeriodTicks <= 0 is the sentinel meaning "same as target" (avoids divide-by-zero
            // on the default-0 codec value and on the tween-from-silence case).
            double fp     = fromPeriodTicks <= 0 ? periodTicks : fromPeriodTicks;
            double period = fp + (periodTicks - fp) * t;
            double phase  = fromPhaseTicks + (phaseTicks - fromPhaseTicks) * t;
            return amp * Math.sin(2.0 * Math.PI * (tick - phase) / period);
        }

        @Override
        public ValueSource settle(long tick) {
            return tweenTicks > 0 && tick - startTick >= tweenTicks
                    ? new SinWave(0.0, amplitude, 0.0, periodTicks, 0.0, phaseTicks, 0L, 0L) : this;
        }

        @SuppressWarnings("null")
        static final MapCodec<SinWave> CODEC = RecordCodecBuilder.<SinWave>mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("fromAmplitude", 0.0).forGetter(SinWave::fromAmplitude),
                Codec.DOUBLE.fieldOf("amplitude").forGetter(SinWave::amplitude),
                Codec.DOUBLE.optionalFieldOf("fromPeriod", 0.0).forGetter(SinWave::fromPeriodTicks),
                Codec.DOUBLE.fieldOf("period").forGetter(SinWave::periodTicks),
                Codec.DOUBLE.optionalFieldOf("fromPhase", 0.0).forGetter(SinWave::fromPhaseTicks),
                Codec.DOUBLE.fieldOf("phase").forGetter(SinWave::phaseTicks),
                Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(SinWave::tweenTicks),
                Codec.LONG.optionalFieldOf("startTick", 0L).forGetter(SinWave::startTick)
        ).apply(i, SinWave::new));
    }

    /**
     * Spatially-varying Perlin noise. Amplitude tweens from {@code fromAmplitude} to
     * {@code amplitude}; spatial/temporal scales snap to the new values immediately.
     */
    record Perlin(
            double xzScale,
            double fromAmplitude,
            double amplitude,
            double timeTicks,
            long tweenTicks,
            long startTick,
            long seed
    ) implements ValueSource {

        private static final Map<Long, ImprovedNoise> NOISE_CACHE = new ConcurrentHashMap<>();

        @Override public String type() { return "perlin"; }

        @Override public double get(long tick) { return get(tick, 0, 0); }

        @Override
        public double get(long tick, double x, double z) {
            double t   = tweenTicks <= 0 ? 1.0
                    : Mth.clamp((double) (tick - startTick) / tweenTicks, 0.0, 1.0);
            double amp = fromAmplitude + (amplitude - fromAmplitude) * t;
            return amp * noise().noise(x * xzScale, tick / timeTicks, z * xzScale);
        }

        @Override
        public ValueSource settle(long tick) {
            return tweenTicks > 0 && tick - startTick >= tweenTicks
                    ? new Perlin(xzScale, 0.0, amplitude, timeTicks, 0L, 0L, seed) : this;
        }

        private ImprovedNoise noise() {
            return NOISE_CACHE.computeIfAbsent(seed, s ->
                    new ImprovedNoise(new XoroshiroRandomSource(s, s ^ 0x9E3779B97F4A7C15L)));
        }

        @SuppressWarnings("null")
        static final MapCodec<Perlin> CODEC = RecordCodecBuilder.<Perlin>mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("xzScale").forGetter(Perlin::xzScale),
                Codec.DOUBLE.optionalFieldOf("fromAmplitude", 0.0).forGetter(Perlin::fromAmplitude),
                Codec.DOUBLE.fieldOf("amplitude").forGetter(Perlin::amplitude),
                Codec.DOUBLE.fieldOf("timeTicks").forGetter(Perlin::timeTicks),
                Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(Perlin::tweenTicks),
                Codec.LONG.optionalFieldOf("startTick", 0L).forGetter(Perlin::startTick),
                Codec.LONG.fieldOf("seed").forGetter(Perlin::seed)
        ).apply(i, Perlin::new));
    }
}
