package org.dudblockman.hostileatmosphere.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Data-pack driven zone definition. Loaded from data/&lt;ns&gt;/zones/&lt;id&gt;.json.
 * Zones are sorted ascending by yCeiling; the lowest ceiling is the most severe.
 * Effective ceiling = yCeiling + atmosphereLevel.
 */
public record ZoneDefinition(
        int yCeiling,
        int hazardTimeSecs,
        int toxinBuildupSecs
) {

    @SuppressWarnings("null") // RecordCodecBuilder unboxes boxed primitives; nulls cannot occur at runtime
    public static final Codec<ZoneDefinition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("yCeiling").forGetter(ZoneDefinition::yCeiling),
                    Codec.INT.fieldOf("hazardTimeSecs").forGetter(ZoneDefinition::hazardTimeSecs),
                    Codec.INT.fieldOf("toxinBuildupSecs").forGetter(ZoneDefinition::toxinBuildupSecs)
            ).apply(instance, ZoneDefinition::new)
    );
}
