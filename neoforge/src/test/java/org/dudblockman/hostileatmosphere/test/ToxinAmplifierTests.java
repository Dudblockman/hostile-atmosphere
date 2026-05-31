package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;

/**
 * Tests {@link AtmosphereEngine#getToxinAmplifier} against default thresholds (250/500/750/950).
 * Uses {@link AtmosphereConfig#getSettings()} — assumes server config is loaded with defaults.
 */
@GameTestHolder(Constants.MOD_ID)
public class ToxinAmplifierTests {

    // NeoForge prepends "{namespace}:{className}." so this resolves to
    // hostileatmosphere:toxinamplifiertests.empty_platform
    private static final String TEMPLATE = "empty_platform";

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void belowThreshold1(GameTestHelper helper) {
        assertAmplifier(0, -1, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void justBelowThreshold1(GameTestHelper helper) {
        assertAmplifier(249, -1, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atThreshold1(GameTestHelper helper) {
        assertAmplifier(250, 0, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void midLevel1(GameTestHelper helper) {
        assertAmplifier(400, 0, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atThreshold2(GameTestHelper helper) {
        assertAmplifier(500, 1, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atThreshold3(GameTestHelper helper) {
        assertAmplifier(750, 2, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atThreshold4(GameTestHelper helper) {
        assertAmplifier(950, 3, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atMax(GameTestHelper helper) {
        assertAmplifier(1000, 3, helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------

    private static void assertAmplifier(int toxin, int expectedAmp, GameTestHelper helper) {
        AtmosphereSettings cfg = AtmosphereConfig.getSettings();
        if (cfg == null) {
            helper.fail("AtmosphereConfig not loaded yet — config must be loaded before tests run");
            return;
        }
        int actual = AtmosphereEngine.getToxinAmplifier(toxin, cfg);
        if (actual != expectedAmp) {
            helper.fail(String.format(
                    "getToxinAmplifier(%d): expected amp %d but was %d", toxin, expectedAmp, actual));
        }
    }
}
