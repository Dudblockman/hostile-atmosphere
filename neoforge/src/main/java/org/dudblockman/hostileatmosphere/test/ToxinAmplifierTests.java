package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;

/**
 * Tests {@link AtmosphereEngine#getToxinAmplifier} against fixed thresholds.
 * Constructs {@link AtmosphereSettings} directly so results are independent of
 * any config file present on disk.
 */
@GameTestHolder(Constants.MOD_ID)
public class ToxinAmplifierTests {

    // NeoForge prepends "{namespace}:{className}." so this resolves to
    // hostileatmosphere:toxinamplifiertests.empty_platform
    private static final String TEMPLATE = "empty_platform";

    // Thresholds under test. Holder fields (toxicityEffect, airDrainRate, toxinRate)
    // are null — getToxinAmplifier does not access them.
    private static final AtmosphereSettings CFG = new AtmosphereSettings(
            30, 3, 10, 30, 1f, 2f, 4f, 1f, 0.75f, 0.5f,
            14400, 250, 500, 750, 950, 500,
            false, 1.5f,
            0.6f, 0.6f, false, 0f, 0f, 0.5f,
            null, null, null);

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void belowThreshold1(GameTestHelper helper) {
        assertAmplifier(0, -1, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void justBelowThreshold1(GameTestHelper helper) {
        assertAmplifier(249, -1, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atThreshold1(GameTestHelper helper) {
        assertAmplifier(250, 0, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void midLevel1(GameTestHelper helper) {
        assertAmplifier(400, 0, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atThreshold2(GameTestHelper helper) {
        assertAmplifier(500, 1, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atThreshold3(GameTestHelper helper) {
        assertAmplifier(750, 2, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atThreshold4(GameTestHelper helper) {
        assertAmplifier(950, 3, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atMax(GameTestHelper helper) {
        assertAmplifier(Constants.MAX_TOXIN, 3, helper);
        helper.succeed();
    }

    private static void assertAmplifier(int toxin, int expectedAmp, GameTestHelper helper) {
        int actual = AtmosphereEngine.getToxinAmplifier(toxin, CFG);
        if (actual != expectedAmp) {
            String msg = String.format("getToxinAmplifier(%d): expected amp %d but was %d", toxin, expectedAmp, actual);
            helper.fail(msg);
            throw new RuntimeException(msg);
        }
    }
}
