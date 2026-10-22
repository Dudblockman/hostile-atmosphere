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
    public static void toxinBelowFirstThresholdGivesNoEffect(GameTestHelper helper) {
        GameTestAssertions.assertEquals("getToxinAmplifier(249)", -1, AtmosphereEngine.getToxinAmplifier(249, CFG), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void toxinAtFirstThresholdGivesAmplifierZero(GameTestHelper helper) {
        GameTestAssertions.assertEquals("getToxinAmplifier(250)", 0, AtmosphereEngine.getToxinAmplifier(250, CFG), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void toxinAtSecondThresholdGivesAmplifierOne(GameTestHelper helper) {
        GameTestAssertions.assertEquals("getToxinAmplifier(500)", 1, AtmosphereEngine.getToxinAmplifier(500, CFG), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void toxinAtThirdThresholdGivesAmplifierTwo(GameTestHelper helper) {
        GameTestAssertions.assertEquals("getToxinAmplifier(750)", 2, AtmosphereEngine.getToxinAmplifier(750, CFG), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void toxinAtFourthThresholdGivesAmplifierThree(GameTestHelper helper) {
        GameTestAssertions.assertEquals("getToxinAmplifier(950)", 3, AtmosphereEngine.getToxinAmplifier(950, CFG), helper);
        helper.succeed();
    }
}
