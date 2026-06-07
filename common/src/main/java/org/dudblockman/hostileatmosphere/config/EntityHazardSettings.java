package org.dudblockman.hostileatmosphere.config;

public record EntityHazardSettings(
        boolean enabled,
        boolean damagePassive,
        boolean damageHostile,
        boolean damageAquatic,
        boolean damageNpc,
        boolean suppressPassive,
        boolean suppressHostile,
        boolean suppressAquatic,
        boolean suppressNpc
) {}
