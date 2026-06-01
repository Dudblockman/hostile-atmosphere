package org.dudblockman.hostileatmosphere.test;

import net.neoforged.neoforge.event.RegisterGameTestsEvent;

public class GameTestRegistration {

    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(ModifierComputationTests.class);
        event.register(ModifierProgressionTests.class);
        event.register(ZoneLookupTests.class);
        event.register(ToxinAmplifierTests.class);
    }
}
