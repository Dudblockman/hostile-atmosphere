package org.dudblockman.hostileatmosphere.compat;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.armor.DivingHelmetItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.dudblockman.hostileatmosphere.engine.ProtectionLevel;

/**
 * Soft dependency on Create. Guards all Create class references inside the
 * static-nested {@link Delegate} class, which the JVM only loads when Create
 * is present on the classpath — so this class is safe to load without Create.
 *
 * Imports above are compile-time aliases only; they do not trigger class
 * loading, so they are safe even when Create is absent at runtime.
 */
public final class CreateCompat {

    private static final boolean LOADED = ModList.get().isLoaded("create");
    private static final String VISUAL_BACKTANK_AIR = "VisualBacktankAir";

    private CreateCompat() {}

    public static ProtectionLevel getProtection(ServerPlayer player) {
        if (!LOADED) return ProtectionLevel.NONE;
        return Delegate.check(player);
    }

    public static void drainBacktank(ServerPlayer player) {
        if (!LOADED) return;
        Delegate.drainBacktank(player, 1);
    }

    public static void drainBacktank(ServerPlayer player, int amount) {
        if (!LOADED || amount <= 0) return;
        Delegate.drainBacktank(player, amount);
    }

    /**
     * Writes (or removes) the VISUAL_BACKTANK_AIR key in the entity's persistent
     * data so Create's RemainingAirOverlay — and any custom overlay — can read
     * the remaining backtank air. Must be called client-side each tick.
     */
    public static void updateVisualAir(LivingEntity entity) {
        if (!LOADED) return;
        Delegate.updateVisualAir(entity);
    }

    public static void clearVisualAir(LivingEntity entity) {
        if (!LOADED) return;
        entity.getPersistentData().remove(VISUAL_BACKTANK_AIR);
    }

    // -------------------------------------------------------------------------
    // All Create class references live here. The JVM will not resolve this
    // class until Delegate.check() is first invoked, which only happens when
    // LOADED is true — i.e. Create is on the classpath.
    // -------------------------------------------------------------------------
    private static final class Delegate {

        static ProtectionLevel check(ServerPlayer player) {
            var chest = player.getItemBySlot(EquipmentSlot.CHEST);

            // --- Tier 3: full Netherite Diving Suit ----------------------------
            // Create sets a 4-bit flag (one per armor slot) in persistent data as
            // netherite diving pieces are equipped; all 4 set = complete suit.
            byte bits = player.getPersistentData().getByte("CreateNetheriteDivingBits");
            if ((bits & 0b1111) == 0b1111 && BacktankUtil.hasAirRemaining(chest)) {
                return ProtectionLevel.SEALED;
            }

            // --- Tier 2: Any diving helmet + backtank with air --------
            // DivingHelmetItem.isWornBy() matches both copper and netherite helmets.
            // Also catches incomplete netherite sets that fell through the SEALED check.
            if (DivingHelmetItem.isWornBy(player) && BacktankUtil.hasAirRemaining(chest)) {
                return ProtectionLevel.RESPIRATOR;
            }

            return ProtectionLevel.NONE;
        }

        static void drainBacktank(ServerPlayer player, int amount) {
            var chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!BacktankUtil.hasAirRemaining(chest)) return;
            BacktankUtil.consumeAir(player, chest, amount);
        }

        static void updateVisualAir(LivingEntity entity) {
            var chest = entity.getItemBySlot(EquipmentSlot.CHEST);
            if (BacktankUtil.hasAirRemaining(chest)) {
                entity.getPersistentData().putInt(VISUAL_BACKTANK_AIR, BacktankUtil.getAir(chest));
            } else {
                entity.getPersistentData().remove(VISUAL_BACKTANK_AIR);
            }
        }
    }
}
