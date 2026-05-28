package org.dudblockman.hostileatmosphere.compat;

public enum ProtectionLevel {
    /** No Create diving equipment, or empty backtank. */
    NONE,
    /**
     * Filled backtank in chest slot, but no diving helmet.
     * Provides no direct air or toxin protection in open air;
     * when submerged counts as Water Breathing for the underwater multipliers.
     */
    BACKTANK_ONLY,
    /**
     * Any Diving Helmet + filled backtank (copper or netherite).
     * Air drain is stopped; toxin seeps slowly through the suit.
     */
    RESPIRATOR,
    /**
     * Full Netherite Diving Suit + filled backtank.
     * Complete immunity — no air drain, no toxin buildup.
     */
    SEALED
}
