package org.dudblockman.hostileatmosphere.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

public class ModRegistries {

    /** Registry key for the data-pack driven zone registry. JSON files live at data/&lt;ns&gt;/zones/&lt;id&gt;.json. */
    @SuppressWarnings("null")
    public static final ResourceKey<Registry<ZoneDefinition>> ZONES =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "zones"));
}
