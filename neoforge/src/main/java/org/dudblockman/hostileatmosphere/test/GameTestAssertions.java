package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTestHelper;

final class GameTestAssertions {

    static final double DELTA = 0.001;

    private GameTestAssertions() {}

    static void assertEquals(double expected, double actual, GameTestHelper helper) {
        if (Math.abs(expected - actual) > DELTA) {
            String msg = String.format("Expected %.4f but was %.4f", expected, actual);
            helper.fail(msg);
            throw new AssertionError(msg);
        }
    }

    static void assertEquals(String label, double expected, double actual, GameTestHelper helper) {
        if (Math.abs(expected - actual) > DELTA) {
            String msg = String.format("%s: expected %.4f but was %.4f", label, expected, actual);
            helper.fail(msg);
            throw new AssertionError(msg);
        }
    }

    static void assertTrue(String label, boolean condition, GameTestHelper helper) {
        if (!condition) {
            String msg = label + ": expected true but was false";
            helper.fail(msg);
            throw new AssertionError(msg);
        }
    }

    static void assertEquals(String label, int expected, int actual, GameTestHelper helper) {
        if (expected != actual) {
            String msg = label + ": expected " + expected + " but was " + actual;
            helper.fail(msg);
            throw new AssertionError(msg);
        }
    }

    static void assertNotNull(String msg, Object obj, GameTestHelper helper) {
        if (obj == null) {
            String m = msg + ": expected non-null but was null";
            helper.fail(m);
            throw new AssertionError(m);
        }
    }

    static void assertNull(String msg, Object obj, GameTestHelper helper) {
        if (obj != null) {
            String m = msg + ": expected null but was " + obj;
            helper.fail(m);
            throw new AssertionError(m);
        }
    }
}
