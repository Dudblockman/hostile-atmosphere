package org.dudblockman.hostileatmosphere.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.RandomSource;
import org.dudblockman.hostileatmosphere.client.AtmosphereClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiHeartsMixin {

    @Shadow private RandomSource random;

    @ModifyVariable(
        method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        at = @At(value = "STORE"),
        name = "l1"
    )
    private int ha_addToxicityWiggle(int l1) {
        if (AtmosphereClientData.forceHeartWiggle) {
            l1 += random.nextInt(2);
        }
        return l1;
    }
}
