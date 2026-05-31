package org.dudblockman.hostileatmosphere.datagen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.dudblockman.hostileatmosphere.Constants;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var gen    = event.getGenerator();
        var output = gen.getPackOutput();
        gen.addProvider(event.includeServer(), new TestStructureProvider(output));
    }
}
