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
import org.dudblockman.hostileatmosphere.compat.ProtectionLevel;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.damage.MiasmaDamageTypes;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import java.util.List;
import java.util.function.Function;

public class AtmosphereEngine {

    private static final String NS = "hostileatmosphere";
    private static final ResourceLocation ID_AIR_PROTECTION  = ResourceLocation.fromNamespaceAndPath(NS, "protection_air");
    private static final ResourceLocation ID_AIR_UNDERWATER  = ResourceLocation.fromNamespaceAndPath(NS, "underwater_air");
    private static final ResourceLocation ID_AIR_RESPIRATION = ResourceLocation.fromNamespaceAndPath(NS, "respiration_air");
    private static final ResourceLocation ID_TOXIN_PROTECTION = ResourceLocation.fromNamespaceAndPath(NS, "protection_toxin");
    private static final ResourceLocation ID_TOXIN_EXPEDITION = ResourceLocation.fromNamespaceAndPath(NS, "expedition_toxin");
    private static final ResourceLocation ID_TOXIN_UNDERWATER = ResourceLocation.fromNamespaceAndPath(NS, "underwater_toxin");

    /**
     * Returns the most severe zone the player is in using a global atmosphere level offset.
     * Zones must be sorted ascending by yCeiling (lowest = most severe first).
     */
    public static ZoneDefinition findZone(List<ZoneDefinition> zones, double eyeY, double atmosphereLevel) {
        for (ZoneDefinition zone : zones) {
            if (eyeY <= zone.yCeiling() + atmosphereLevel) return zone;
        }
        return null;
    }

    /**
     * Returns the most severe zone, using per-zone level offsets.
     * {@code zoneIds} is parallel to {@code zones}; {@code levelForZone} maps a zone id → its level offset.
     */
    public static ZoneDefinition findZone(List<ZoneDefinition> zones, List<String> zoneIds,
                                          double eyeY, Function<String, Double> levelForZone) {
        for (int i = 0; i < zones.size(); i++) {
            String id = i < zoneIds.size() ? zoneIds.get(i) : "all";
            if (eyeY <= zones.get(i).yCeiling() + levelForZone.apply(id)) return zones.get(i);
        }
        return null;
    }

    /** activeZone is pre-computed by the caller via {@link #findZone}. */
    public static void tick(ServerPlayer player, PlayerAtmosphereData data, AtmosphereSettings cfg,
                            ProtectionLevel protection, ZoneDefinition activeZone) {
        if (data.needsInit()) {
            data.setGracePeriodTicks(cfg.gracePeriodDays() * 24000);
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
        // SEALED supplies clean air → recovers even in a hazard zone.
        // RESPIRATOR pauses drain; debt stays frozen until the player leaves.
        boolean doAirRecovery = activeZone == null || protection == ProtectionLevel.SEALED;

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

        // Miasma damage only fires when the player has zero air AND no protection.
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
        } else {
            // In hazard zone, toxinMult == 0 (IMMUNE) — suspended, neither builds nor drains.
            data.setToxinAccumulator(0f);
            data.setToxinRecoveryAccumulator(0f);
        }

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
        syncModifier(airInst,   ID_AIR_UNDERWATER,   inWater ? underwaterAirMult(player, protection, cfg)   - 1.0 : Double.NaN);
        syncModifier(airInst,   ID_AIR_RESPIRATION,  resp > 0 ? 1.0 / (1.0 + 0.5 * resp) - 1.0 : Double.NaN);
        syncModifier(toxinInst, ID_TOXIN_PROTECTION, protection == ProtectionLevel.SEALED ? -1.0 : Double.NaN);
        syncModifier(toxinInst, ID_TOXIN_EXPEDITION, protection == ProtectionLevel.RESPIRATOR ? cfg.expeditionToxinMultiplier() - 1.0 : Double.NaN);
        syncModifier(toxinInst, ID_TOXIN_UNDERWATER, inWater ? underwaterToxinMult(player, protection, cfg) - 1.0 : Double.NaN);
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

    private static double underwaterAirMult(ServerPlayer player, ProtectionLevel protection,
                                             AtmosphereSettings cfg) {
        if (player.hasEffect(MobEffects.CONDUIT_POWER)) {
            return cfg.conduitPurification()
                    ? cfg.conduitPurificationAirDebtMultiplier()
                    : cfg.underwaterAirDebtMultiplier();
        }
        if (player.hasEffect(MobEffects.WATER_BREATHING)
                || player.getItemBySlot(EquipmentSlot.HEAD).is(Items.TURTLE_HELMET)
                || protection == ProtectionLevel.BACKTANK_ONLY) {
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
                || protection == ProtectionLevel.BACKTANK_ONLY
                || protection == ProtectionLevel.RESPIRATOR) {
            return cfg.underwaterToxinMultiplier();
        }
        // Turtle Helmet: no toxin benefit — full rate.
        return 1.0;
    }

    // ==========================================================================================
    // Air debt helpers
    // ==========================================================================================

    private static void accumulateDrain(PlayerAtmosphereData data, int maxAir,
                                         ZoneDefinition zone, float rateMult) {
        float drainPerTick = ((float) maxAir / (zone.hazardTimeSecs() * 20f)) * rateMult;
        float acc = data.getDrainAccumulator() + drainPerTick;
        int units = (int) acc;
        if (units > 0) {
            data.setAirDebt(Math.min(data.getAirDebt() + units, maxAir));
            acc -= units;
        }
        data.setDrainAccumulator(acc);
    }

    private static void accumulateRecovery(PlayerAtmosphereData data, int maxAir, AtmosphereSettings cfg) {
        float recoveryPerTick = (float) maxAir / (cfg.safeZoneRecoverySecs() * 20f);
        float acc = data.getRecoveryAccumulator() + recoveryPerTick;
        int units = (int) acc;
        if (units > 0) {
            data.setAirDebt(Math.max(data.getAirDebt() - units, 0));
            acc -= units;
        }
        data.setRecoveryAccumulator(acc);
    }

    private static void applyMiasmaDamage(ServerPlayer player, PlayerAtmosphereData data, AtmosphereSettings cfg) {
        int suff = data.getSuffocationTicks();
        int tier2 = cfg.rampTier2Secs() * 20;
        int tier3 = cfg.rampTier3Secs() * 20;

        float damage;
        int interval;

        if (suff >= tier3) {
            damage   = cfg.rampDamageTier3();
            interval = Math.max(1, Math.round(cfg.rampIntervalTier3Secs() * 20));
        } else if (suff >= tier2) {
            damage   = cfg.rampDamageTier2();
            interval = Math.max(1, Math.round(cfg.rampIntervalTier2Secs() * 20));
        } else {
            damage   = cfg.rampDamageTier1();
            interval = Math.max(1, Math.round(cfg.rampIntervalTier1Secs() * 20));
        }

        if (player.tickCount % interval == 0) {
            var key = (suff >= tier3) ? MiasmaDamageTypes.MIASMA_INTENSE : MiasmaDamageTypes.MIASMA;
            DamageSource miasma = new DamageSource(
                    player.level().registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(key)
            );
            player.hurt(miasma, damage);
        }
    }

    // ==========================================================================================
    // Enchantment helpers
    // ==========================================================================================

    private static int getRespirationLevel(ServerPlayer player) {
        var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return 0;
        var respHolder = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.RESPIRATION);
        return helmet.getEnchantments().getLevel(respHolder);
    }

    // ==========================================================================================
    // Toxin helpers
    // ==========================================================================================

    private static void accumulateToxin(PlayerAtmosphereData data, ZoneDefinition zone, float rateMult) {
        float buildupPerTick = (1000f / (zone.toxinBuildupSecs() * 20f)) * rateMult;
        float acc = data.getToxinAccumulator() + buildupPerTick;
        int units = (int) acc;
        if (units > 0) {
            data.setToxinLevel(Math.min(data.getToxinLevel() + units, 1000));
            acc -= units;
        }
        data.setToxinAccumulator(acc);
    }

    private static void recoverToxin(PlayerAtmosphereData data, AtmosphereSettings cfg) {
        if (data.getToxinLevel() == 0) return;
        float recoveryPerTick = 1000f / (cfg.toxinRecoverySecs() * 20f);
        float acc = data.getToxinRecoveryAccumulator() + recoveryPerTick;
        int units = (int) acc;
        if (units > 0) {
            data.setToxinLevel(Math.max(data.getToxinLevel() - units, 0));
            acc -= units;
        }
        data.setToxinRecoveryAccumulator(acc);
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
        } else if ((activeZone == null || protection == ProtectionLevel.SEALED) && data.getAirDebt() > 0) {
            airDebtPerSec = -(maxAir / (float) cfg.safeZoneRecoverySecs());
        } else {
            airDebtPerSec = 0f;
        }

        float toxinPerSec;
        if (activeZone != null && toxinMult > 0) {
            toxinPerSec = (1000f / activeZone.toxinBuildupSecs()) * toxinMult;
        } else if (activeZone == null && data.getToxinLevel() > 0) {
            toxinPerSec = -(1000f / cfg.toxinRecoverySecs());
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
        if (existing != null && existing.getDuration() != -1) return;

        if (existing != null) player.removeEffect(effectHolder);
        if (targetAmplifier >= 0) {
            player.addEffect(new MobEffectInstance(effectHolder, -1, targetAmplifier, true, true, true));
        }
    }

}
