package org.dudblockman.hostileatmosphere.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.dudblockman.hostileatmosphere.Constants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the vanilla low-health heart wiggle to Atmospheric Toxicity Level II–III (amps 1–2). */
@Mixin(Gui.class)
public abstract class GuiHeartsMixin {

    @Unique private static final ResourceLocation TOXICITY_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "atmospheric_toxicity");

    @Shadow private RandomSource random;

    @Unique private boolean hostileatmosphere$forceWiggle; // set each frame before renderHearts

    // Sets forceWiggle: amp 1–2 and health > 4 (vanilla handles health ≤ 4; level IV uses colour).
    @Inject(
        method = "renderHealthLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"
        )
    )
    private void ha_captureWiggleState(CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            hostileatmosphere$forceWiggle = false;
            return;
        }
        MobEffectInstance inst = player.getActiveEffects().stream()
                .filter(e -> e.getEffect().unwrapKey()
                        .map(k -> TOXICITY_ID.equals(k.location()))
                        .orElse(false))
                .findFirst().orElse(null);
        int amp = (inst != null) ? inst.getAmplifier() : -1;
        hostileatmosphere$forceWiggle = (amp == 1 || amp == 2)
                && player.getHealth() + player.getAbsorptionAmount() > 4.0F;
    }

    // Mirrors vanilla: l1 += random.nextInt(2) — fires for our toxicity condition.
    @ModifyVariable(
        method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        at = @At(value = "STORE"),
        name = "l1"
    )
    private int ha_addToxicityWiggle(int l1) {
        if (hostileatmosphere$forceWiggle) {
            l1 += random.nextInt(2);
        }
        return l1;
    }
}
