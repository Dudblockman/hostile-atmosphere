package org.dudblockman.hostileatmosphere.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import java.util.HashMap;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.ai.FleeHazardGoal;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.engine.EntityHazardEngine;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class EntityHazardEventHandler {

    private static final HashMap<UUID, EntityHazardEngine.EntityAirState> airStateMap = new HashMap<>();

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;

        AtmosphereSettings atmosphereSettings = AtmosphereConfig.getSettings();
        if (atmosphereSettings == null) return;

        if (!EntityHazardEngine.isSubjectToHazard(living)) return;
        if (living.isDeadOrDying()) {
            airStateMap.remove(living.getUUID());
            return;
        }
        if (!AtmosphereConfig.isDamageEnabled(living.getType())) return;
        if (!(living.level() instanceof ServerLevel serverLevel)) return;

        ZoneDefinition activeZone = ZoneLookup.findZoneAt(serverLevel, living.getX(), living.getEyeY(), living.getZ());
        EntityHazardEngine.EntityAirState current = airStateMap.getOrDefault(living.getUUID(), EntityHazardEngine.EntityAirState.ZERO);
        EntityHazardEngine.EntityAirState next = EntityHazardEngine.tickAirDebt(living, current, activeZone, atmosphereSettings);

        if (next == EntityHazardEngine.EntityAirState.ZERO) {
            airStateMap.remove(living.getUUID());
        } else {
            airStateMap.put(living.getUUID(), next);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        AtmosphereSettings.EntityHazardSettings cfg = AtmosphereSettings.getEntityHazardSettings();
        if (cfg == null) return;
        if (!cfg.isDamageEnabled(mob.getType())) return;
        mob.goalSelector.addGoal(2, new FleeHazardGoal(mob, EntityHazardEventHandler::getAirDebt));
    }

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.PATROL) return;

        AtmosphereSettings.EntityHazardSettings cfg = AtmosphereSettings.getEntityHazardSettings();
        if (cfg == null) return;

        EntityType<?> type = event.getEntityType();
        if (!EntityHazardEngine.isSubjectToHazard(type)) return;
        if (!cfg.isSuppressEnabled(type)) return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = event.getPos();
        ZoneDefinition zone = ZoneLookup.findZoneAt(serverLevel, pos.getX() + 0.5, spawnEyeY(pos, type), pos.getZ() + 0.5);
        if (zone == null) return;

        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    public static int getAirDebt(LivingEntity entity) {
        EntityHazardEngine.EntityAirState state = airStateMap.get(entity.getUUID());
        return state != null ? state.airDebt() : 0;
    }

    private static double spawnEyeY(BlockPos pos, EntityType<?> type) {
        EntityDimensions dims = type.getDimensions();
        return pos.getY() + dims.height() * 0.85;
    }
}
