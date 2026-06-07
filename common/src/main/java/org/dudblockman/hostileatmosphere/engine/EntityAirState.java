package org.dudblockman.hostileatmosphere.engine;

/**
 * Per-entity atmosphere state used by {@link EntityHazardEngine}.
 * Stored in a {@code WeakHashMap} in the event handler; discarded when the entity is GC'd.
 *
 * airDebt          – atmosphere-consumed air ticks; 0–maxAir
 * drainAccumulator – fractional drain units pending; fire when ≥ 1
 * suffocationTicks – continuous ticks at zero effective air; drives Miasma damage interval
 */
public record EntityAirState(
        int airDebt,
        float drainAccumulator,
        int suffocationTicks
) {
    public static final EntityAirState ZERO = new EntityAirState(0, 0f, 0);
}
