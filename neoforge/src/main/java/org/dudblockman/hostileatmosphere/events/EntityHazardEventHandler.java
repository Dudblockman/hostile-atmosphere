package org.dudblockman.hostileatmosphere.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.config.EntityHazardSettings;
import org.dudblockman.hostileatmosphere.engine.EntityHazardEngine;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class EntityHazardEventHandler {

    private static final Map<LivingEntity, EntityHazardEngine.EntityAirState> entityAirState = new WeakHashMap<>();

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;

        EntityHazardSettings cfg = AtmosphereConfig.getEntityHazardSettings();
        if (cfg == null) return;
        AtmosphereSettings atmosphereSettings = AtmosphereConfig.getSettings();
        if (atmosphereSettings == null) return;

        if (!EntityHazardEngine.isSubjectToHazard(living)) return;
        if (living.isDeadOrDying()) {
            entityAirState.remove(living);
            return;
        }
        if (!EntityHazardEngine.isDamageEnabled(living.getType(), cfg)) return;
        if (!(living.level() instanceof ServerLevel serverLevel)) return;

        ZoneDefinition activeZone = ZoneLookup.findZoneAt(serverLevel, living.getX(), living.getEyeY(), living.getZ());
        EntityHazardEngine.EntityAirState current = entityAirState.getOrDefault(living, EntityHazardEngine.EntityAirState.ZERO);
        EntityHazardEngine.EntityAirState next = EntityHazardEngine.tickAirDebt(living, current, activeZone, atmosphereSettings);

        if (next == EntityHazardEngine.EntityAirState.ZERO) {
            entityAirState.remove(living);
        } else {
            entityAirState.put(living, next);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!EntityHazardEngine.isSubjectToHazard(mob.getType())) return;
        mob.goalSelector.addGoal(2, new FleeHazardGoal(mob));
    }

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.PATROL) return;

        EntityHazardSettings cfg = AtmosphereConfig.getEntityHazardSettings();
        if (cfg == null) return;

        EntityType<?> type = event.getEntityType();
        if (!EntityHazardEngine.isSubjectToHazard(type)) return;
        if (!EntityHazardEngine.isSuppressEnabled(type, cfg)) return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = event.getPos();
        EntityDimensions dims = type.getDimensions();
        double eyeY = pos.getY() + dims.height() * 0.85;
        ZoneDefinition zone = ZoneLookup.findZoneAt(serverLevel, pos.getX() + 0.5, eyeY, pos.getZ() + 0.5);
        if (zone == null) return;

        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }
}
