package org.dudblockman.hostileatmosphere.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface ValueSource {

    double get(long tick);

    default double get(long tick, double x, double z) { return get(tick); }

    String type();

    /** Returns a settled copy with tweenTicks=0 once the tween is complete, or {@code this} if still in progress. */
    default ValueSource settle(long tick) { return this; }

    /**
     * Called once per server tick to update any server-side state.
     * Returns {@code true} if persistent state changed this tick (signals the caller to
     * mark the containing SavedData dirty). The default is a no-op returning {@code false}.
     */
    default boolean serverTick(ServerLevel level, long tick) { return false; }

    @SuppressWarnings("null")
    Codec<ValueSource> CODEC = Codec.STRING.dispatch("type", ValueSource::type, type -> ValueSource.BY_TYPE.get(type));

    @SuppressWarnings({"null", "unchecked"})
    Map<String, MapCodec<? extends ValueSource>> BY_TYPE = Map.ofEntries(
            Map.entry("constant",  Constant.CODEC),
            Map.entry("sin",       SinWave.CODEC),
            Map.entry("perlin",    Perlin.CODEC),
            Map.entry("drift",     Drift.CODEC),
            Map.entry("predicate", PredicateSource.CODEC)
    );

    private static double tweenProgress(long tick, long startTick, long tweenTicks) {
        return tweenTicks <= 0 ? 1.0 : Mth.clamp((double) (tick - startTick) / tweenTicks, 0.0, 1.0);
    }

    private static Map<Long, ImprovedNoise> makeNoiseCache(int capacity) {
        return Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, ImprovedNoise> eldest) {
                return size() > capacity;
            }
        });
    }

    record Constant(double fromValue, double value, long tweenTicks, long startTick) implements ValueSource {

        @Override public String type() { return "constant"; }

        @Override
        public double get(long tick) {
            return Mth.lerp(tweenProgress(tick, startTick, tweenTicks), fromValue, value);
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
            double t      = tweenProgress(tick, startTick, tweenTicks);
            double amp    = Mth.lerp(t, fromAmplitude, amplitude);
            // fromPeriodTicks <= 0 is the sentinel meaning "same as target" (avoids divide-by-zero
            // on the default-0 codec value and on the tween-from-silence case).
            double fp     = fromPeriodTicks <= 0 ? periodTicks : fromPeriodTicks;
            double period = Mth.lerp(t, fp, periodTicks);
            double phase  = Mth.lerp(t, fromPhaseTicks, phaseTicks);
            if (period == 0.0) return 0.0;
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

        private static final Map<Long, ImprovedNoise> NOISE_CACHE = makeNoiseCache(32);

        @Override public String type() { return "perlin"; }

        @Override public double get(long tick) { return get(tick, 0, 0); }

        @Override
        public double get(long tick, double x, double z) {
            double amp = Mth.lerp(tweenProgress(tick, startTick, tweenTicks), fromAmplitude, amplitude);
            double timeCoord = timeTicks == 0.0 ? 0.0 : (double) tick / timeTicks;
            double raw = amp * noise().noise(x * xzScale, timeCoord, z * xzScale);
            return Mth.clamp(raw, -Math.abs(amp), Math.abs(amp));
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

    /**
     * Time-only Perlin noise: samples a 1-D slice of noise so the result is the same at every
     * world position but drifts smoothly up and down as the tick advances. Amplitude tweens from
     * {@code fromAmplitude} to {@code amplitude}; {@code timeTicks} controls how many ticks
     * elapse per unit of noise coordinate (higher = slower drift).
     */
    record Drift(
            double fromAmplitude,
            double amplitude,
            double timeTicks,
            long tweenTicks,
            long startTick,
            long seed
    ) implements ValueSource {

        private static final Map<Long, ImprovedNoise> NOISE_CACHE = makeNoiseCache(32);

        @Override public String type() { return "drift"; }

        @Override
        public double get(long tick) {
            double amp = Mth.lerp(tweenProgress(tick, startTick, tweenTicks), fromAmplitude, amplitude);
            double timeCoord = timeTicks == 0.0 ? 0.0 : (double) tick / timeTicks;
            double raw = amp * noise().noise(timeCoord, 0.0, 0.0);
            return Mth.clamp(raw, -Math.abs(amp), Math.abs(amp));
        }

        @Override
        public ValueSource settle(long tick) {
            return tweenTicks > 0 && tick - startTick >= tweenTicks
                    ? new Drift(0.0, amplitude, timeTicks, 0L, 0L, seed) : this;
        }

        private ImprovedNoise noise() {
            return NOISE_CACHE.computeIfAbsent(seed, s ->
                    new ImprovedNoise(new XoroshiroRandomSource(s, s ^ 0x9E3779B97F4A7C15L)));
        }

        @SuppressWarnings("null")
        static final MapCodec<Drift> CODEC = RecordCodecBuilder.<Drift>mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("fromAmplitude", 0.0).forGetter(Drift::fromAmplitude),
                Codec.DOUBLE.fieldOf("amplitude").forGetter(Drift::amplitude),
                Codec.DOUBLE.fieldOf("timeTicks").forGetter(Drift::timeTicks),
                Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(Drift::tweenTicks),
                Codec.LONG.optionalFieldOf("startTick", 0L).forGetter(Drift::startTick),
                Codec.LONG.fieldOf("seed").forGetter(Drift::seed)
        ).apply(i, Drift::new));
    }
}
