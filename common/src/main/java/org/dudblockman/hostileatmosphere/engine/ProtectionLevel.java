package org.dudblockman.hostileatmosphere.engine;

public enum ProtectionLevel {
    /** No Create diving equipment, or empty backtank. */
    NONE,
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
