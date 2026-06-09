package org.dudblockman.hostileatmosphere.engine;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;

public class AtmosphereEngine {

    private static final ResourceLocation ID_AIR_PROTECTION  = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,"protection_air");
    private static final ResourceLocation ID_AIR_UNDERWATER  = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,"underwater_air");
    private static final ResourceLocation ID_AIR_RESPIRATION = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,"respiration_air");
    private static final ResourceLocation ID_TOXIN_PROTECTION = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,"protection_toxin");
    private static final ResourceLocation ID_TOXIN_EXPEDITION = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,"expedition_toxin");
    private static final ResourceLocation ID_TOXIN_UNDERWATER = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,"underwater_toxin");
    private static final ResourceLocation ID_TOXIN_RAIN       = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,"rain_toxin");

    /**
     * Returns the most severe zone the player is in using a global atmosphere level offset.
     * Zones must be sorted ascending by evalCeiling (lowest = most severe first).
     * {@code tick}, {@code x}, {@code z} are passed to each zone's ceiling pipeline.
     */
    public static ZoneDefinition findZone(List<ZoneDefinition> zones,
                                          long tick, double x, double eyeY, double z,
                                          double atmosphereLevel) {
        for (ZoneDefinition zone : zones) {
            if (eyeY <= zone.evalCeiling(tick, x, z) + atmosphereLevel) return zone;
        }
        return null;
    }

    /**
     * Returns the most severe zone using per-zone effective ceilings.
     * {@code zoneIds} is parallel to {@code zones}.
     * {@code effectiveCeiling} receives (zoneId, baseCeiling) and returns the absolute
     * effective ceiling for that zone; returning {@link Double#NaN} skips the zone entirely,
     * allowing data packs to disable zones by clearing all their modifiers.
     */
    public static ZoneDefinition findZone(List<ZoneDefinition> zones, List<String> zoneIds,
                                          long tick, double x, double eyeY, double z,
                                          BiFunction<String, Double, Double> effectiveCeiling) {
        if (zones.size() != zoneIds.size())
            throw new IllegalArgumentException(
                "zones and zoneIds must be parallel lists of equal size: "
                + zones.size() + " vs " + zoneIds.size());
        for (int i = 0; i < zones.size(); i++) {
            double base = zones.get(i).evalCeiling(tick, x, z);
            String id   = zoneIds.get(i);
            double ceil = effectiveCeiling.apply(id, base);
            if (!Double.isNaN(ceil) && eyeY <= ceil) return zones.get(i);
        }
        return null;
    }

    /** activeZone is pre-computed by the caller via {@link #findZone}. */
    public static void tick(ServerPlayer player, PlayerAtmosphereData data, AtmosphereSettings cfg,
                            ProtectionLevel protection, ZoneDefinition activeZone) {
        if (data.needsInit()) {
            data.setGracePeriodTicks(cfg.gracePeriodDays() * Constants.TICKS_PER_DAY);
        }

        if (data.getGracePeriodTicks() > 0) {
            data.setGracePeriodTicks(data.getGracePeriodTicks() - 1);
            return;
        }

        int maxAir = player.getMaxAirSupply();

        applyRateModifiers(player, protection, cfg);

        float airMult   = getAttributeValue(player, cfg.airDrainRate());
        float toxinMult = getAttributeValue(player, cfg.toxinRate());

        boolean fullyProtected = protection == ProtectionLevel.SEALED
                || protection == ProtectionLevel.RESPIRATOR;

        // ----- Air debt -----------------------------------------------------------------------

        boolean doAirDrain    = activeZone != null && airMult > 0;
        // Both SEALED and RESPIRATOR recover debt while in-zone; the backtank pays the cost
        // (see AtmosphereEventHandler.onPlayerTick — retroactive drain). Outside the zone,
        // recovery is free (passive safe-air breathing).
        boolean doAirRecovery = activeZone == null
                || protection == ProtectionLevel.SEALED
                || protection == ProtectionLevel.RESPIRATOR;

        if (doAirDrain) {
            accumulateDrain(data, maxAir, activeZone, airMult);
            data.setRecoveryAccumulator(0f);
        } else if (doAirRecovery && data.getAirDebt() > 0) {
            accumulateRecovery(data, maxAir, cfg);
            data.setDrainAccumulator(0f);
        } else {
            data.setDrainAccumulator(0f);
            data.setRecoveryAccumulator(0f);
        }

        int ceiling = maxAir - data.getAirDebt();
        if (player.getAirSupply() > ceiling) {
            player.setAirSupply(ceiling);
        }

        if (data.getAirDebt() >= maxAir && !fullyProtected) {
            data.setSuffocationTicks(data.getSuffocationTicks() + 1);
            applyMiasmaDamage(player, data, cfg);
        } else if (data.getSuffocationTicks() > 0) {
            data.setSuffocationTicks(0);
        }

        // ----- Toxin buildup ------------------------------------------------------------------

        if (activeZone != null && toxinMult > 0) {
            accumulateToxin(data, activeZone, toxinMult);
            data.setToxinRecoveryAccumulator(0f);
        } else if (activeZone == null) {
            recoverToxin(data, cfg);
            data.setToxinAccumulator(0f);
        }
        // activeZone != null && toxinMult == 0 (IMMUNE): leave accumulators unchanged.

        int targetAmp = getToxinAmplifier(data.getToxinLevel(), cfg);
        applyToxicityEffect(player, targetAmp, cfg.toxicityEffect());
    }

    // ==========================================================================================
    // Attribute modifier application
    // ==========================================================================================

    /**
     * Updates HA-owned transient modifiers on the player's air_drain_rate and toxin_rate
     * attributes. Each modifier is dirty-checked independently against the value already on the
     * AttributeInstance — the instance is the source of truth, so no separate cache is needed.
     *
     * NaN = modifier should not be present. doubleToLongBits gives NaN == NaN.
     * ADD_MULTIPLIED_TOTAL: final = base × Π(1 + value). Modifier value for multiplier M = M − 1.0.
     */
    private static void applyRateModifiers(ServerPlayer player, ProtectionLevel protection,
                                            AtmosphereSettings cfg) {
        var airInst   = player.getAttribute(cfg.airDrainRate());
        var toxinInst = player.getAttribute(cfg.toxinRate());
        if (airInst == null || toxinInst == null) return;

        boolean inWater = player.isEyeInFluid(FluidTags.WATER);
        int     resp    = getRespirationLevel(player);

        syncModifier(airInst,   ID_AIR_PROTECTION,   (protection == ProtectionLevel.SEALED || protection == ProtectionLevel.RESPIRATOR) ? -1.0 : Double.NaN);
        syncModifier(airInst,   ID_AIR_UNDERWATER,   inWater ? underwaterAirMult(player, cfg)   - 1.0 : Double.NaN);
        syncModifier(airInst,   ID_AIR_RESPIRATION,  resp > 0 ? 1.0 / (1.0 + 0.5 * resp) - 1.0 : Double.NaN);
        syncModifier(toxinInst, ID_TOXIN_PROTECTION, protection == ProtectionLevel.SEALED ? -1.0 : Double.NaN);
        syncModifier(toxinInst, ID_TOXIN_EXPEDITION, protection == ProtectionLevel.RESPIRATOR ? cfg.expeditionToxinMultiplier() - 1.0 : Double.NaN);
        syncModifier(toxinInst, ID_TOXIN_UNDERWATER, inWater ? underwaterToxinMult(player, protection, cfg) - 1.0 : Double.NaN);
        syncModifier(toxinInst, ID_TOXIN_RAIN,       cfg.rainToxinMultiplierEnabled() && (player.level().isRainingAt(player.blockPosition()) || (player.level().isRaining() && player.isInWater())) ? cfg.rainToxinMultiplier() - 1.0 : Double.NaN);
    }

    private static void syncModifier(AttributeInstance inst, ResourceLocation id, double desired) {
        AttributeModifier existing = inst.getModifier(id);
        boolean shouldExist = !Double.isNaN(desired);
        if (existing == null && !shouldExist) return;
        if (existing != null && shouldExist
                && Double.doubleToLongBits(existing.amount()) == Double.doubleToLongBits(desired)) return;
        if (existing != null) inst.removeModifier(id);
        if (shouldExist)      inst.addTransientModifier(mult(id, desired));
    }

    private static AttributeModifier mult(ResourceLocation id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static float getAttributeValue(ServerPlayer player, Holder<Attribute> attr) {
        var inst = player.getAttribute(attr);
        return inst == null ? 1.0f : (float) Math.max(0.0, inst.getValue());
    }

    // ==========================================================================================
    // Protection helpers (return the raw multiplier for underwater conditions)
    // ==========================================================================================

    private static double underwaterAirMult(ServerPlayer player, AtmosphereSettings cfg) {
        if (player.hasEffect(MobEffects.CONDUIT_POWER)) {
            return cfg.conduitPurification()
                    ? cfg.conduitPurificationAirDebtMultiplier()
                    : cfg.underwaterAirDebtMultiplier();
        }
        if (player.hasEffect(MobEffects.WATER_BREATHING)
                || player.getItemBySlot(EquipmentSlot.HEAD).is(Items.TURTLE_HELMET)) {
            return cfg.underwaterAirDebtMultiplier();
        }
        return 1.0;
    }

    private static double underwaterToxinMult(ServerPlayer player, ProtectionLevel protection,
                                               AtmosphereSettings cfg) {
        if (player.hasEffect(MobEffects.CONDUIT_POWER)) {
            return cfg.conduitPurification()
                    ? cfg.conduitPurificationToxinMultiplier()
                    : cfg.underwaterToxinMultiplier();
        }
        if (player.hasEffect(MobEffects.WATER_BREATHING)
                || protection == ProtectionLevel.RESPIRATOR) {
            return cfg.underwaterToxinMultiplier();
        }
        // Turtle Helmet: no toxin benefit — full rate.
        return 1.0;
    }

    // ==========================================================================================
    // Air debt helpers
    // ==========================================================================================

    private static float accumulate(float acc, float ratePerTick, IntConsumer applyUnits) {
        acc += ratePerTick;
        int units = (int) acc;
        if (units > 0) {
            applyUnits.accept(units);
            acc -= units;
        }
        return acc;
    }

    private static void accumulateDrain(PlayerAtmosphereData data, int maxAir,
                                         ZoneDefinition zone, float rateMult) {
        float rate = ((float) maxAir / (zone.hazardTimeSecs() * 20f)) * rateMult;
        data.setDrainAccumulator(accumulate(data.getDrainAccumulator(), rate,
                u -> data.setAirDebt(Math.min(data.getAirDebt() + u, maxAir))));
    }

    private static void accumulateRecovery(PlayerAtmosphereData data, int maxAir, AtmosphereSettings cfg) {
        float rate = (float) maxAir / (cfg.safeZoneRecoverySecs() * 20f);
        data.setRecoveryAccumulator(accumulate(data.getRecoveryAccumulator(), rate,
                u -> data.setAirDebt(Math.max(data.getAirDebt() - u, 0))));
    }

    private static void applyMiasmaDamage(ServerPlayer player, PlayerAtmosphereData data, AtmosphereSettings cfg) {
        int suff = data.getSuffocationTicks();
        int tier2 = cfg.rampTier2Secs() * 20;
        int tier3 = cfg.rampTier3Secs() * 20;

        float damage;
        int interval;

        if (suff >= tier3) {
            damage   = cfg.rampDamageTier3();
            interval = MiasmaDamageTypes.toIntervalTicks(cfg.rampIntervalTier3Secs());
        } else if (suff >= tier2) {
            damage   = cfg.rampDamageTier2();
            interval = MiasmaDamageTypes.toIntervalTicks(cfg.rampIntervalTier2Secs());
        } else {
            damage   = cfg.rampDamageTier1();
            interval = MiasmaDamageTypes.toIntervalTicks(cfg.rampIntervalTier1Secs());
        }

        if (suff % interval == 0) {
            DamageSource source = (suff >= tier3)
                    ? MiasmaDamageTypes.miasmaIntense(player)
                    : MiasmaDamageTypes.miasma(player);
            player.hurt(source, damage);
        }
    }

    // ==========================================================================================
    // Enchantment helpers
    // ==========================================================================================

    private static int getRespirationLevel(ServerPlayer player) {
        var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return 0;
        return player.level().registryAccess()
                .lookup(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.get(Enchantments.RESPIRATION))
                .map(h -> helmet.getEnchantments().getLevel(h))
                .orElse(0);
    }

    // ==========================================================================================
    // Toxin helpers
    // ==========================================================================================

    private static void accumulateToxin(PlayerAtmosphereData data, ZoneDefinition zone, float rateMult) {
        float rate = (Constants.MAX_TOXIN / (zone.toxinBuildupSecs() * 20f)) * rateMult;
        data.setToxinAccumulator(accumulate(data.getToxinAccumulator(), rate,
                u -> data.setToxinLevel(Math.min(data.getToxinLevel() + u, Constants.MAX_TOXIN))));
    }

    private static void recoverToxin(PlayerAtmosphereData data, AtmosphereSettings cfg) {
        if (data.getToxinLevel() == 0) return;
        float rate = Constants.MAX_TOXIN / (cfg.toxinRecoverySecs() * 20f);
        data.setToxinRecoveryAccumulator(accumulate(data.getToxinRecoveryAccumulator(), rate,
                u -> data.setToxinLevel(Math.max(data.getToxinLevel() - u, 0))));
    }

    /** Current per-second rates of change for air debt and toxin level. */
    public record Rates(float airDebtPerSec, float toxinPerSec) {}

    /**
     * Computes display rates for the debug command.
     * activeZone is pre-computed by the caller via {@link #findZone}; null = safe zone.
     */
    public static Rates computeRates(ServerPlayer player, PlayerAtmosphereData data,
                                     AtmosphereSettings cfg, ProtectionLevel protection,
                                     ZoneDefinition activeZone) {
        int maxAir = player.getMaxAirSupply();

        // Modifiers are maintained by tick(); just read the current attribute values.
        float airMult   = getAttributeValue(player, cfg.airDrainRate());
        float toxinMult = getAttributeValue(player, cfg.toxinRate());

        float airDebtPerSec;
        if (activeZone != null && airMult > 0) {
            airDebtPerSec = (maxAir / (float) activeZone.hazardTimeSecs()) * airMult;
        } else if ((activeZone == null || protection == ProtectionLevel.SEALED || protection == ProtectionLevel.RESPIRATOR) && data.getAirDebt() > 0) {
            airDebtPerSec = -(maxAir / (float) cfg.safeZoneRecoverySecs());
        } else {
            airDebtPerSec = 0f;
        }

        float toxinPerSec;
        if (activeZone != null && toxinMult > 0) {
            toxinPerSec = ((float) Constants.MAX_TOXIN / activeZone.toxinBuildupSecs()) * toxinMult;
        } else if (activeZone == null && data.getToxinLevel() > 0) {
            toxinPerSec = -(Constants.MAX_TOXIN / (float) cfg.toxinRecoverySecs());
        } else {
            toxinPerSec = 0f;
        }

        return new Rates(airDebtPerSec, toxinPerSec);
    }

    public static int getToxinAmplifier(int toxinLevel, AtmosphereSettings cfg) {
        if (toxinLevel >= cfg.toxinThreshold4()) return 3;
        if (toxinLevel >= cfg.toxinThreshold3()) return 2;
        if (toxinLevel >= cfg.toxinThreshold2()) return 1;
        if (toxinLevel >= cfg.toxinThreshold1()) return 0;
        return -1;
    }

    private static void applyToxicityEffect(ServerPlayer player, int targetAmplifier,
                                             Holder<MobEffect> effectHolder) {
        var existing = player.getEffect(effectHolder);
        int currentAmplifier = (existing != null) ? existing.getAmplifier() : -1;

        if (targetAmplifier == currentAmplifier) return;

        if (existing != null) player.removeEffect(effectHolder);
        if (targetAmplifier >= 0) {
            player.addEffect(new MobEffectInstance(effectHolder, -1, targetAmplifier, true, true, true));
        }
    }

}
