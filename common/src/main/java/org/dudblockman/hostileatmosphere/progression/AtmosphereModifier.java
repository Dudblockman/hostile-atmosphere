package org.dudblockman.hostileatmosphere.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

public record AtmosphereModifier(
        int key,
        Operation operation,
        ValueSource source,
        /** Zone ID this modifier applies to, or "all" for all zones. */
        String target
) {

    public enum Operation implements StringRepresentable {
        ADD("offset"), CLAMP_MAX("cap"), CLAMP_MIN("floor");

        private final String name;

        Operation(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }

        public static final Codec<Operation> CODEC = StringRepresentable.fromEnum(Operation::values);

        public double apply(double running, double value) {
            return switch (this) {
                case ADD       -> running + value;
                case CLAMP_MAX -> Math.min(running, value);
                case CLAMP_MIN -> Math.max(running, value);
            };
        }
    }

    @SuppressWarnings("null")
    public static final Codec<AtmosphereModifier> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("key").forGetter(AtmosphereModifier::key),
                    Operation.CODEC.fieldOf("operation").forGetter(AtmosphereModifier::operation),
                    ValueSource.CODEC.fieldOf("source").forGetter(AtmosphereModifier::source),
                    Codec.STRING.optionalFieldOf("target", "all").forGetter(AtmosphereModifier::target)
            ).apply(instance, AtmosphereModifier::new)
    );

    public double getCurrentValue(long tick) {
        return source.get(tick);
    }

    public double getCurrentValue(long tick, double x, double z) {
        return source.get(tick, x, z);
    }
}
