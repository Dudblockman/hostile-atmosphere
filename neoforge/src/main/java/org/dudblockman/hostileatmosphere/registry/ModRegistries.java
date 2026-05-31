package org.dudblockman.hostileatmosphere.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

@SuppressWarnings("null")
public class ModRegistries {

    /** Registry key for the data-pack driven zone registry. JSON files live at data/<ns>/zones/<id>.json. */
    public static final ResourceKey<Registry<ZoneDefinition>> ZONES =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "zones"));

    public static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(ZONES, ZoneDefinition.CODEC);
    }
}
