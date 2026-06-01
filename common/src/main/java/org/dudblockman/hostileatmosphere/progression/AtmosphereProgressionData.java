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
     * Per-zone level: evaluates "all" modifiers plus those targeting {@code zoneId},
     * in ascending key order (pipeline model).
     */
    public double getLevelForZone(long tick, double x, double z, String zoneId) {
        double level = 0.0;
        for (AtmosphereModifier mod : modifiers.values()) {
            String t = mod.target();
            if (!t.equals("all") && !t.equals(zoneId)) continue;
            double v = mod.getCurrentValue(tick, x, z);
            level = switch (mod.operation()) {
                case ADD       -> level + v;
                case CLAMP_MAX -> Math.min(level, v);
                case CLAMP_MIN -> Math.max(level, v);
            };
        }
        return level;
    }

    /**
     * Raw pipeline computation ignoring target — exposed for tests.
     * Test modifiers default to target="all" so this gives the same result as filtered.
     */
    public static double computeLevel(Iterable<AtmosphereModifier> modifiers, long tick) {
        double level = 0.0;
        for (AtmosphereModifier mod : modifiers) {
            double v = mod.getCurrentValue(tick);
            level = switch (mod.operation()) {
                case ADD       -> level + v;
                case CLAMP_MAX -> Math.min(level, v);
                case CLAMP_MIN -> Math.max(level, v);
            };
        }
        return level;
    }

    // ------------------------------------------------------------------------------------------

    /** Adds key 0 as a no-op constant if it has never been set. Called on zone load. */
    public void ensureKey0() {
        if (!modifiers.containsKey(0)) {
            modifiers.put(0, new AtmosphereModifier(0, AtmosphereModifier.Operation.ADD,
                    new ValueSource.Constant(0, 0, 0), "all"));
            setDirty();
        }
    }

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
