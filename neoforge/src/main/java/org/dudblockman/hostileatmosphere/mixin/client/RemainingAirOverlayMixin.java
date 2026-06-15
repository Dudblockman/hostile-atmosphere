package org.dudblockman.hostileatmosphere.mixin.client;

import com.simibubi.create.content.equipment.armor.RemainingAirOverlay;
import net.minecraft.client.player.LocalPlayer;
import org.dudblockman.hostileatmosphere.client.AtmosphereClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Create's RemainingAirOverlay visible when the player is in a hazard zone
 * with a backtank — bypassing the "must be submerged or in lava" guard.
 *
 * The guard is: if ((isAir || canBreathe) && !player.isInLava()) return;
 * Redirecting isInLava() to return true when divingActive makes !isInLava() == false,
 * collapsing the entire condition to false and allowing the overlay to render.
 */
@Pseudo
@Mixin(value = RemainingAirOverlay.class, remap = false)
public class RemainingAirOverlayMixin {

    @Redirect(
            method = "render",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isInLava()Z",
                    remap = false
            )
    )
    private boolean hostileatmosphere$bypassSubmergedGuard(LocalPlayer player) {
        if (AtmosphereClientData.isDivingActive(player.getUUID())) {
            return true;
        }
        return player.isInLava();
    }
}
