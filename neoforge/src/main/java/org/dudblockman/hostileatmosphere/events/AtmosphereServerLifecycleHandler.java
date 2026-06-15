package org.dudblockman.hostileatmosphere.events;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneCacheManager;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereServerLifecycleHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long tick = overworld.getGameTime();
        // PredicateSource evaluates predicates against the overworld regardless of zone dimension
        // because serverTick() needs zone-to-dimension mapping that isn't plumbed through yet.
        AtmosphereProgressionData.get(server).serverTick(overworld, tick);
        ZoneCacheManager.tickAllZoneSources(server, tick);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ZoneCacheManager.rebuildZoneCache(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ZoneCacheManager.rebuildZoneCache(event.getPlayerList().getServer());
    }
}
