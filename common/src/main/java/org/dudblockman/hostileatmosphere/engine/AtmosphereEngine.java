package org.dudblockman.hostileatmosphere.engine;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.damage.MiasmaDamageTypes;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;

public class AtmosphereEngine {

    public static void tick(ServerPlayer player, PlayerAtmosphereData data, AtmosphereSettings cfg) {
        if (data.needsInit()) {
            data.setGracePeriodTicks(cfg.gracePeriodDays() * 24000);
        }

        if (data.getGracePeriodTicks() > 0) {
            data.setGracePeriodTicks(data.getGracePeriodTicks() - 1);
            return;
        }

        int maxAir = player.getMaxAirSupply();
        boolean inHazard = Mth.floor(player.getY()) <= cfg.dangerYThreshold();

        if (inHazard) {
            accumulateDrain(data, maxAir, cfg);
            data.setRecoveryAccumulator(0f);
        } else if (data.getAirDebt() > 0) {
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

        if (data.getAirDebt() >= maxAir) {
            data.setSuffocationTicks(data.getSuffocationTicks() + 1);
            applyMiasmaDamage(player, data, cfg);
        } else if (data.getSuffocationTicks() > 0) {
            data.setSuffocationTicks(0);
        }
    }

    /**
     * Fractional accumulator drain.
     * drainPerTick = maxAir / (hazardTimeSecs * 20), which may be non-integer.
     * Units are applied as whole numbers whenever the accumulator reaches 1.
     */
    private static void accumulateDrain(PlayerAtmosphereData data, int maxAir, AtmosphereSettings cfg) {
        float drainPerTick = (float) maxAir / (cfg.hazardTimeSecs() * 20f);
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
}
