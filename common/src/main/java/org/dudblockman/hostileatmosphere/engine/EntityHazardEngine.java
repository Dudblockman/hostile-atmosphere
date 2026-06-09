package org.dudblockman.hostileatmosphere.engine;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.config.EntityHazardSettings;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModEntityTags;

public class EntityHazardEngine {

    public record EntityAirState(int airDebt, float drainAccumulator, int suffocationTicks) {
        public static final EntityAirState ZERO = new EntityAirState(0, 0f, 0);
    }

    public static boolean isSubjectToHazard(LivingEntity entity) {
        if (entity instanceof Player) return false;
        return !entity.getType().is(ModEntityTags.HAZARD_EXEMPT);
    }

    public static boolean isSubjectToHazard(EntityType<?> type) {
        if (type == EntityType.PLAYER) return false;
        return !type.is(ModEntityTags.HAZARD_EXEMPT);
    }

    public static boolean isDamageEnabled(EntityType<?> type, EntityHazardSettings cfg) {
        if (!cfg.enabled()) return false;
        return matchesAny(type,
                new CategoryCheck(cfg.damagePassive(),  ModEntityTags.HAZARD_DAMAGE_PASSIVE),
                new CategoryCheck(cfg.damageHostile(),  ModEntityTags.HAZARD_DAMAGE_HOSTILE),
                new CategoryCheck(cfg.damageAquatic(),  ModEntityTags.HAZARD_DAMAGE_AQUATIC),
                new CategoryCheck(cfg.damageNpc(),      ModEntityTags.HAZARD_DAMAGE_NPC));
    }

    public static boolean isSuppressEnabled(EntityType<?> type, EntityHazardSettings cfg) {
        if (!cfg.enabled()) return false;
        return matchesAny(type,
                new CategoryCheck(cfg.suppressPassive(),  ModEntityTags.SPAWN_SUPPRESS_PASSIVE),
                new CategoryCheck(cfg.suppressHostile(),  ModEntityTags.SPAWN_SUPPRESS_HOSTILE),
                new CategoryCheck(cfg.suppressAquatic(),  ModEntityTags.SPAWN_SUPPRESS_AQUATIC),
                new CategoryCheck(cfg.suppressNpc(),      ModEntityTags.SPAWN_SUPPRESS_NPC));
    }

    /**
     * Advances one tick of air-debt simulation for an entity.
     * Air drains over the zone's {@code hazardTimeSecs}; when fully drained the entity takes
     * Miasma damage every 20 ticks. Debt clears instantly on zone exit.
     *
     * @param entity     the entity being ticked
     * @param state      current per-entity state; use {@link EntityAirState#ZERO} on first entry
     * @param activeZone the zone the entity is currently in, or {@code null} if in safe air
     * @return the updated state; {@link EntityAirState#ZERO} when safe or debt fully cleared
     */
    public static EntityAirState tickAirDebt(LivingEntity entity, EntityAirState state,
                                              ZoneDefinition activeZone, AtmosphereSettings cfg) {
        if (activeZone == null) return EntityAirState.ZERO;
        int maxAir = entity.getMaxAirSupply();
        float rate = (float) maxAir / (activeZone.hazardTimeSecs() * 20f);
        float acc = state.drainAccumulator() + rate;
        int units = (int) acc;
        acc -= units;
        int newDebt = Math.min(state.airDebt() + units, maxAir);
        int newSuff = newDebt >= maxAir ? state.suffocationTicks() + 1 : 0;
        int interval = MiasmaDamageTypes.toIntervalTicks(cfg.rampIntervalTier1Secs());
        if (newDebt >= maxAir && newSuff % interval == 0) {
            entity.hurt(MiasmaDamageTypes.miasma(entity), cfg.rampDamageTier1());
        }
        return new EntityAirState(newDebt, acc, newSuff);
    }

    private record CategoryCheck(boolean flag, TagKey<EntityType<?>> tag) {
        boolean matches(EntityType<?> type) { return flag && type.is(tag); }
    }

    private static boolean matchesAny(EntityType<?> type, CategoryCheck... checks) {
        for (CategoryCheck c : checks) if (c.matches(type)) return true;
        return false;
    }
}
