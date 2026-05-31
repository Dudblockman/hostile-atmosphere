package org.dudblockman.hostileatmosphere.datagen;

import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Generates NBT structure templates used by GameTests.
 * Run via ./gradlew :neoforge:runData — output lands in src/generated/resources.
 */
public class TestStructureProvider implements DataProvider {

    private final PackOutput output;

    public TestStructureProvider(PackOutput output) {
        this.output = output;
    }

    // NeoForge resolves @GameTest(template="empty_platform") as {namespace}:{className}.empty_platform.
    // Each test class needs its own structure file with that dotted name.
    private static final String[] TEST_CLASS_PREFIXES = {
            "modifiercomputationtests",
            "zonelookuptests",
            "toxinamplifiertests",
    };

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.runAsync(() -> {
            try {
                CompoundTag platform = emptyPlatform();
                for (String prefix : TEST_CLASS_PREFIXES) {
                    save(cache, platform, resolve(prefix + ".empty_platform.nbt"));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public String getName() {
        return "HA Test Structures";
    }

    // ------------------------------------------------------------------------------------------

    private Path resolve(String name) {
        return output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve("hostileatmosphere/structure/" + name);
    }

    private static void save(CachedOutput cache, CompoundTag nbt, Path path) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, buf);
        byte[] bytes = buf.toByteArray();
        Files.createDirectories(path.getParent());
        cache.writeIfNeeded(path, bytes,
                com.google.common.hash.Hashing.sha256().hashBytes(bytes));
    }

    /**
     * 5×5 stone floor, 4 blocks of air above — enough headroom for all engine tests.
     * Size is [5, 5, 5]; only the floor (y=0) contains blocks; the rest is implicit air.
     */
    private static CompoundTag emptyPlatform() {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion",
                SharedConstants.getCurrentVersion().getDataVersion().getVersion());

        root.put("size", intList(5, 5, 5));

        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                CompoundTag entry = new CompoundTag();
                entry.put("pos", intList(x, 0, z));
                entry.putInt("state", 0);
                blocks.add(entry);
            }
        }
        root.put("blocks", blocks);
        root.put("entities", new ListTag());

        return root;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int v : values) list.add(IntTag.valueOf(v));
        return list;
    }
}
