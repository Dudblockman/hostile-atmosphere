package org.dudblockman.hostileatmosphere.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.dudblockman.hostileatmosphere.Constants;

public class AtmosphericToxicityEffect extends MobEffect {

    private static final ResourceLocation WEAKNESS_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "toxicity_weakness");

    public AtmosphericToxicityEffect() {
        super(MobEffectCategory.HARMFUL, 0x3D6B2E);
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        var inst = attributeMap.getInstance(Attributes.ATTACK_DAMAGE);
        if (inst != null) {
            inst.removeModifier(WEAKNESS_ID);
            inst.addTransientModifier(new AttributeModifier(WEAKNESS_ID, -2.0 * (amplifier + 1), AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        var inst = attributeMap.getInstance(Attributes.ATTACK_DAMAGE);
        if (inst != null) inst.removeModifier(WEAKNESS_ID);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return super.shouldApplyEffectTickThisTick(duration, amplifier);
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
