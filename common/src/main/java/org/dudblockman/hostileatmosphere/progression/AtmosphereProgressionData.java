package org.dudblockman.hostileatmosphere.progression;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.dudblockman.hostileatmosphere.Constants;

import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings("null")
public class AtmosphereProgressionData extends SavedData {

    private static final String DATA_NAME = Constants.MOD_ID + "_progression";
    private static final Factory<AtmosphereProgressionData> FACTORY =
            new Factory<>(AtmosphereProgressionData::new, AtmosphereProgressionData::load, null);

    /** Integer key → modifier, evaluated in ascending key order. Key 0 = data pack base. */
    private final TreeMap<Integer, AtmosphereModifier> modifiers = new TreeMap<>();

    private AtmosphereProgressionData() {}

    public static AtmosphereProgressionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ------------------------------------------------------------------------------------------

    /**
     * Global level: only evaluates modifiers with target="all".
     * Used for the debug display and as a fallback.
     */
    public double getLevel(long tick) {
        return getLevelForZone(tick, 0, 0, "all");
    }

    /**
     * Runs the runtime modifier pipeline for {@code zoneId} starting from {@code baseCeiling}
     * (the datapack-defined ceiling from {@code ZoneDefinition.evalCeiling}).
     * ADD offsets the running ceiling; CLAMP_MAX/CLAMP_MIN cap/floor it at absolute y-values.
     * Returns {@code baseCeiling} unchanged when no runtime modifier matches — the zone stays
     * active at its datapack-defined ceiling with no adjustments.
     */
    public double getEffectiveCeiling(long tick, double x, double z, String zoneId, double baseCeiling) {
        double effective = baseCeiling;
        for (var entry : modifiers.entrySet()) {
            AtmosphereModifier mod = entry.getValue();
            if (!mod.target().equals("all") && !mod.target().equals(zoneId)) continue;
            ValueSource settled = mod.source().settle(tick);
            if (settled != mod.source()) {
                mod = new AtmosphereModifier(mod.key(), mod.operation(), settled, mod.target());
                entry.setValue(mod);
                setDirty();
            }
            effective = mod.operation().apply(effective, mod.getCurrentValue(tick, x, z));
        }
        return effective;
    }

    /**
     * Convenience for unit tests: evaluates the runtime pipeline with baseCeiling=0,
     * so ADD modifiers report their net offset relative to the datapack floor.
     */
    public double getLevelForZone(long tick, double x, double z, String zoneId) {
        return getEffectiveCeiling(tick, x, z, zoneId, 0.0);
    }

    /**
     * Raw pipeline computation ignoring target — exposed for tests.
     * Test modifiers default to target="all" so this gives the same result as filtered.
     */
    public static double computeLevel(Iterable<AtmosphereModifier> modifiers, long tick) {
        double level = 0.0;
        for (AtmosphereModifier mod : modifiers) {
            level = mod.operation().apply(level, mod.getCurrentValue(tick));
        }
        return level;
    }

    // ------------------------------------------------------------------------------------------

    public void setModifier(int key, AtmosphereModifier.Operation op, ValueSource source, String target) {
        modifiers.put(key, new AtmosphereModifier(key, op, source, target));
        setDirty();
    }

    public void removeModifier(int key) {
        if (modifiers.remove(key) != null) setDirty();
    }

    public void clearModifiers() {
        if (!modifiers.isEmpty()) {
            modifiers.clear();
            setDirty();
        }
    }

    public int clearModifiersForTarget(String target) {
        int before = modifiers.size();
        modifiers.values().removeIf(mod -> mod.target().equals(target));
        int removed = before - modifiers.size();
        if (removed > 0) setDirty();
        return removed;
    }

    public Map<Integer, AtmosphereModifier> getModifiers() {
        return Map.copyOf(modifiers);
    }

    // ------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (AtmosphereModifier mod : modifiers.values()) {
            AtmosphereModifier.CODEC.encodeStart(NbtOps.INSTANCE, mod)
                    .result().ifPresent(list::add);
        }
        tag.put("modifiers", list);
        return tag;
    }

    private static AtmosphereProgressionData load(CompoundTag tag, HolderLookup.Provider registries) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        ListTag list = tag.getList("modifiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            AtmosphereModifier.CODEC.decode(NbtOps.INSTANCE, list.get(i))
                    .result()
                    .ifPresent(pair -> data.modifiers.put(pair.getFirst().key(), pair.getFirst()));
        }
        return data;
    }
}
