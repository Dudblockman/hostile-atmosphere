package org.dudblockman.hostileatmosphere.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Data-pack driven zone definition. Loaded from data/&lt;ns&gt;/zones/&lt;id&gt;.json.
 * Zones are sorted ascending by {@link #evalCeiling evalCeiling(0,0,0)} within each dimension;
 * the lowest ceiling is the most severe.
 * Effective ceiling = evalCeiling(tick,x,z) + atmosphereProgressionLevel.
 */
public record ZoneDefinition(
        ResourceLocation dimension,
        List<CeilingLayer> ceiling,
        int hazardTimeSecs,
        int toxinBuildupSecs
) {

    /**
     * One step in the ceiling computation pipeline — mirrors the runtime modifier system
     * so data packs can express the same add / cap / floor logic directly in JSON.
     */
    public record CeilingLayer(AtmosphereModifier.Operation operation, ValueSource source) {

        @SuppressWarnings("null")
        public static final Codec<CeilingLayer> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        AtmosphereModifier.Operation.CODEC.fieldOf("operation").forGetter(CeilingLayer::operation),
                        ValueSource.CODEC.fieldOf("source").forGetter(CeilingLayer::source)
                ).apply(instance, CeilingLayer::new));
    }

    /** Evaluates the ceiling pipeline at the given game tick and world-space X/Z. */
    public double evalCeiling(long tick, double x, double z) {
        return evalPipeline(ceiling, tick, x, z);
    }

    /** Evaluates an arbitrary {@link CeilingLayer} pipeline. */
    public static double evalPipeline(List<CeilingLayer> layers, long tick, double x, double z) {
        double level = 0.0;
        for (CeilingLayer layer : layers) {
            level = layer.operation().apply(level, layer.source().get(tick, x, z));
        }
        return level;
    }

    /** Convenience factory: single constant-value ceiling — used in tests and built-in defaults. */
    public static ZoneDefinition ofConstant(ResourceLocation dim, int yCeiling,
                                            int hazardTimeSecs, int toxinBuildupSecs) {
        return new ZoneDefinition(dim,
                List.of(new CeilingLayer(AtmosphereModifier.Operation.ADD,
                        new ValueSource.Constant(0.0, yCeiling, 0, 0))),
                hazardTimeSecs, toxinBuildupSecs);
    }

    @SuppressWarnings("null")
    public static final Codec<ZoneDefinition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC
                            .optionalFieldOf("dimension",
                                    ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
                            .forGetter(ZoneDefinition::dimension),
                    CeilingLayer.CODEC.listOf().fieldOf("ceiling").forGetter(ZoneDefinition::ceiling),
                    Codec.intRange(1, 72000).fieldOf("hazardTimeSecs").forGetter(ZoneDefinition::hazardTimeSecs),
                    Codec.intRange(1, 72000).fieldOf("toxinBuildupSecs").forGetter(ZoneDefinition::toxinBuildupSecs)
            ).apply(instance, ZoneDefinition::new)
    );
}
