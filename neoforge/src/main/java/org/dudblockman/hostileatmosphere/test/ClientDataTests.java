package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.client.AtmosphereClientData;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.assertTrue;

@GameTestHolder(Constants.MOD_ID)
public class ClientDataTests {

    private static final String TEMPLATE = "empty_platform";

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void resetSessionClearsHeartWiggle(GameTestHelper helper) {
        AtmosphereClientData.forceHeartWiggle = true;
        try {
            AtmosphereClientData.resetSession();
            assertTrue("forceHeartWiggle cleared by resetSession", !AtmosphereClientData.forceHeartWiggle, helper);
        } finally {
            AtmosphereClientData.forceHeartWiggle = false;
        }
        helper.succeed();
    }
}
