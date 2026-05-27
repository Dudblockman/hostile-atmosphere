package org.dudblockman.hostileatmosphere;

public class CommonClass {

    /**
     * Called by every loader's entry point. Put initialization that must run on all
     * platforms here. Loader-specific wiring (attachments, config specs, event buses)
     * stays in the loader entry point.
     */
    public static void init() {
        Constants.LOG.info("{} initializing", Constants.MOD_NAME);
    }
}
