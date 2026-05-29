package org.dudblockman.hostileatmosphere.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.RandomSource;
import org.dudblockman.hostileatmosphere.data.AtmosphereClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiHeartsMixin {

    @Shadow private RandomSource random;

    @Unique private boolean hostileatmosphere$forceWiggle;

    @Inject(
        method = "renderHealthLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"
        )
    )
    private void ha_captureWiggleState(CallbackInfo ci) {
        hostileatmosphere$forceWiggle = AtmosphereClientData.isForceHeartWiggle();
    }

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
