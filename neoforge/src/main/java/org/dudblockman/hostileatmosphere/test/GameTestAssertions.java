package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTestHelper;

final class GameTestAssertions {

    static final double DELTA = 0.001;

    private GameTestAssertions() {}

    static void assertEquals(double expected, double actual, GameTestHelper helper) {
        if (Math.abs(expected - actual) > DELTA)
            helper.fail(String.format("Expected %.4f but was %.4f", expected, actual));
    }

    static void assertEquals(String label, int expected, int actual, GameTestHelper helper) {
        if (expected != actual)
            helper.fail(label + ": expected " + expected + " but was " + actual);
    }

    static void assertNotNull(String msg, Object obj, GameTestHelper helper) {
        if (obj == null) helper.fail(msg + ": expected non-null but was null");
    }

    static void assertNull(String msg, Object obj, GameTestHelper helper) {
        if (obj != null) helper.fail(msg + ": expected null but was " + obj);
    }
}
