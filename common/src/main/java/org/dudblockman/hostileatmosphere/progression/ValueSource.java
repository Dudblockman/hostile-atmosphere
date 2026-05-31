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
 * Every source type has an optional ramp-in: output scales from 0 to full over tweenTicks
 * ticks starting at startTick. tweenTicks=0 (the default) means instant.
 */
public interface ValueSource {

    double get(long tick);

    default double get(long tick, double x, double z) { return get(tick); }

    String type();

    @SuppressWarnings({"null", "unchecked"})
    Map<String, MapCodec<? extends ValueSource>> BY_TYPE = Map.of(
            "constant", Constant.CODEC,
            "sin",      SinWave.CODEC,
            "perlin",   Perlin.CODEC
    );

    Codec<ValueSource> CODEC = Codec.STRING.dispatch("type", ValueSource::type, BY_TYPE::get);

    // ------------------------------------------------------------------------------------------

    record Constant(double value, long tweenTicks, long startTick) implements ValueSource {

        @Override public String type() { return "constant"; }

        @Override
        public double get(long tick) {
            double ramp = tweenTicks <= 0 ? 1.0
                    : Mth.clamp((double) (tick - startTick) / tweenTicks, 0.0, 1.0);
            return value * ramp;
        }

        @SuppressWarnings("null")
        static final MapCodec<Constant> CODEC = RecordCodecBuilder.<Constant>mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("value").forGetter(Constant::value),
                Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(Constant::tweenTicks),
                Codec.LONG.optionalFieldOf("startTick", 0L).forGetter(Constant::startTick)
        ).apply(i, Constant::new));
    }

    record SinWave(double amplitude, double periodTicks, double phaseTicks,
                   long tweenTicks, long startTick) implements ValueSource {

        @Override public String type() { return "sin"; }

        @Override
        public double get(long tick) {
            double ramp = tweenTicks <= 0 ? 1.0
                    : Mth.clamp((double) (tick - startTick) / tweenTicks, 0.0, 1.0);
            return amplitude * ramp * Math.sin(2.0 * Math.PI * (tick - phaseTicks) / periodTicks);
        }

        @SuppressWarnings("null")
        static final MapCodec<SinWave> CODEC = RecordCodecBuilder.<SinWave>mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("amplitude").forGetter(SinWave::amplitude),
                Codec.DOUBLE.fieldOf("period").forGetter(SinWave::periodTicks),
                Codec.DOUBLE.fieldOf("phase").forGetter(SinWave::phaseTicks),
                Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(SinWave::tweenTicks),
                Codec.LONG.optionalFieldOf("startTick", 0L).forGetter(SinWave::startTick)
        ).apply(i, SinWave::new));
    }

    /**
     * Spatially-varying Perlin noise with a ramp-in.
     * Sampled at (x*xzScale, tick/timeTicks, z*xzScale), scaled by amplitude * ramp.
     */
    record Perlin(
            double xzScale,
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
            double ramp = tweenTicks <= 0 ? 1.0
                    : Mth.clamp((double) (tick - startTick) / tweenTicks, 0.0, 1.0);
            return amplitude * ramp * noise().noise(x * xzScale, tick / timeTicks, z * xzScale);
        }

        private ImprovedNoise noise() {
            return NOISE_CACHE.computeIfAbsent(seed, s ->
                    new ImprovedNoise(new XoroshiroRandomSource(s, s ^ 0x9E3779B97F4A7C15L)));
        }

        @SuppressWarnings("null")
        static final MapCodec<Perlin> CODEC = RecordCodecBuilder.<Perlin>mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("xzScale").forGetter(Perlin::xzScale),
                Codec.DOUBLE.fieldOf("amplitude").forGetter(Perlin::amplitude),
                Codec.DOUBLE.fieldOf("timeTicks").forGetter(Perlin::timeTicks),
                Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(Perlin::tweenTicks),
                Codec.LONG.optionalFieldOf("startTick", 0L).forGetter(Perlin::startTick),
                Codec.LONG.fieldOf("seed").forGetter(Perlin::seed)
        ).apply(i, Perlin::new));
    }
}
