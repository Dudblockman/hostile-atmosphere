package org.dudblockman.hostileatmosphere.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.dudblockman.hostileatmosphere.Constants;

public class AtmosphericToxicityEffect extends MobEffect {

    private static final ResourceLocation WEAKNESS_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "toxicity_weakness");

    public AtmosphericToxicityEffect() {
        super(MobEffectCategory.HARMFUL, 0x3D6B2E);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, WEAKNESS_ID, -2.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        if (amplifier >= 2) {
            int interval = (amplifier >= 3) ? 20 : 25;
            return (duration % interval) == 0;
        }
        return false;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (amplifier >= 3) {
            entity.hurt(entity.damageSources().wither(), 1.0f);
        } else if (amplifier >= 2) {
            if (entity.getHealth() > 1.0f) {
                entity.hurt(entity.damageSources().magic(), 1.0f);
            }
        }
        return true;
    }
}
