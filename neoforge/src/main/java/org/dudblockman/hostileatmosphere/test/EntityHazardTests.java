package org.dudblockman.hostileatmosphere.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.engine.EntityHazardEngine;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.OVERWORLD;
import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.TEMPLATE;

/** Tests for {@link EntityHazardEngine}. */
@GameTestHolder(Constants.MOD_ID)
public class EntityHazardTests {

    private static final AtmosphereSettings CFG = AtmosphereSettings.defaults();

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void airDebtAccumulatesInZone(GameTestHelper helper) {
        // hazardTimeSecs=1: rate = 300/20 = 15/tick; 10 ticks → debt = 150
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        ZoneDefinition zone = ZoneDefinition.ofConstant(OVERWORLD, 100, 1, 60);
        EntityHazardEngine.EntityAirState state = EntityHazardEngine.EntityAirState.ZERO;
        for (int i = 0; i < 10; i++) {
            state = EntityHazardEngine.tickAirDebt(cow, state, zone, CFG);
        }
        GameTestAssertions.assertEquals("airDebt after 10 ticks", 150, state.airDebt(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void airDebtCapsAtMaxAirSupply(GameTestHelper helper) {
        // hazardTimeSecs=1: full drain in 20 ticks; 25 ticks must cap at 300
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        ZoneDefinition zone = ZoneDefinition.ofConstant(OVERWORLD, 100, 1, 60);
        EntityHazardEngine.EntityAirState state = EntityHazardEngine.EntityAirState.ZERO;
        for (int i = 0; i < 25; i++) {
            state = EntityHazardEngine.tickAirDebt(cow, state, zone, CFG);
        }
        GameTestAssertions.assertEquals("airDebt capped at maxAir", cow.getMaxAirSupply(), state.airDebt(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void suffocationTicksIncrementAtMaxDebt(GameTestHelper helper) {
        // After 20 ticks at hazardTimeSecs=1, debt hits max and suffocationTicks begins counting
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        ZoneDefinition zone = ZoneDefinition.ofConstant(OVERWORLD, 100, 1, 60);
        EntityHazardEngine.EntityAirState state = EntityHazardEngine.EntityAirState.ZERO;
        for (int i = 0; i < 20; i++) {
            state = EntityHazardEngine.tickAirDebt(cow, state, zone, CFG);
        }
        GameTestAssertions.assertEquals("airDebt at max", cow.getMaxAirSupply(), state.airDebt(), helper);
        GameTestAssertions.assertTrue("suffocationTicks > 0 at max debt", state.suffocationTicks() > 0, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void stateIsZeroWhenOutOfZone(GameTestHelper helper) {
        // Debt clears instantly on zone exit (no recovery ramp for mobs)
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        EntityHazardEngine.EntityAirState state = new EntityHazardEngine.EntityAirState(150, 0.5f, 3);
        EntityHazardEngine.EntityAirState result = EntityHazardEngine.tickAirDebt(cow, state, null, CFG);
        GameTestAssertions.assertTrue("out-of-zone returns ZERO", result == EntityHazardEngine.EntityAirState.ZERO, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void damageDisabledWhenMasterOff(GameTestHelper helper) {
        AtmosphereSettings.EntityHazardSettings cfg = settings(false, true, true, true, true);
        GameTestAssertions.assertTrue("isDamageEnabled returns false when master off",
                !cfg.isDamageEnabled(EntityType.COW), helper);
        GameTestAssertions.assertTrue("isSuppressEnabled returns false when master off",
                !cfg.isSuppressEnabled(EntityType.COW), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void damagePassiveOnByDefault(GameTestHelper helper) {
        AtmosphereSettings.EntityHazardSettings cfg = defaultSettings();
        GameTestAssertions.assertTrue("enabled default true", cfg.enabled(), helper);
        GameTestAssertions.assertTrue("damagePassive default true", cfg.damagePassive(), helper);
        GameTestAssertions.assertTrue("damageHostile default false", !cfg.damageHostile(), helper);
        GameTestAssertions.assertTrue("damageNpc default false", !cfg.damageNpc(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void suppressPassiveOnByDefault(GameTestHelper helper) {
        AtmosphereSettings.EntityHazardSettings cfg = defaultSettings();
        GameTestAssertions.assertTrue("suppressPassive default true", cfg.suppressPassive(), helper);
        GameTestAssertions.assertTrue("suppressNpc default true", cfg.suppressNpc(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void suppressHostileOffByDefault(GameTestHelper helper) {
        AtmosphereSettings.EntityHazardSettings cfg = defaultSettings();
        GameTestAssertions.assertTrue("suppressHostile default false", !cfg.suppressHostile(), helper);
        GameTestAssertions.assertTrue("suppressAquatic default false", !cfg.suppressAquatic(), helper);
        helper.succeed();
    }

    /** Default settings as shipped (master on, passive damage on, passive+npc suppression on). */
    private static AtmosphereSettings.EntityHazardSettings defaultSettings() {
        return new AtmosphereSettings.EntityHazardSettings(
                true, true, false, false, false,
                true, false, false, true);
    }

    /** Settings with the given master toggle; suppression flags at defaults (passive+npc=true). */
    private static AtmosphereSettings.EntityHazardSettings settings(boolean enabled,
            boolean damagePassive, boolean damageHostile,
            boolean damageAquatic, boolean damageNpc) {
        return new AtmosphereSettings.EntityHazardSettings(
                enabled, damagePassive, damageHostile, damageAquatic, damageNpc,
                true, false, false, true);
    }
}
