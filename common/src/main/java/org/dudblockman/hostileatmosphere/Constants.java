package org.dudblockman.hostileatmosphere;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID   = "hostileatmosphere";
    public static final String MOD_NAME = "Hostile Atmosphere";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    /** Maximum toxin level — the ceiling of the toxin meter and the codec/config range bound. */
    public static final int MAX_TOXIN = 1000;

    /** Ticks in one Minecraft in-game day. */
    public static final int TICKS_PER_DAY = 24000;
}
