package org.dudblockman.hostileatmosphere.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.dudblockman.hostileatmosphere.Constants;

public final class ModEntityTags {

    public static final TagKey<EntityType<?>> HAZARD_DAMAGE_PASSIVE  = tag("hazard_damage/passive");
    public static final TagKey<EntityType<?>> HAZARD_DAMAGE_HOSTILE  = tag("hazard_damage/hostile");
    public static final TagKey<EntityType<?>> HAZARD_DAMAGE_AQUATIC  = tag("hazard_damage/aquatic");
    public static final TagKey<EntityType<?>> HAZARD_DAMAGE_NPC      = tag("hazard_damage/npc");

    public static final TagKey<EntityType<?>> SPAWN_SUPPRESS_PASSIVE = tag("spawn_suppress/passive");
    public static final TagKey<EntityType<?>> SPAWN_SUPPRESS_HOSTILE = tag("spawn_suppress/hostile");
    public static final TagKey<EntityType<?>> SPAWN_SUPPRESS_AQUATIC = tag("spawn_suppress/aquatic");
    public static final TagKey<EntityType<?>> SPAWN_SUPPRESS_NPC     = tag("spawn_suppress/npc");

    public static final TagKey<EntityType<?>> HAZARD_EXEMPT          = tag("hazard_exempt");

    private ModEntityTags() {}

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
    }
}
