package com.urntt.elytra.mixin;

import com.urntt.elytra.ElytraClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	/**
	 * Lets Autopilot set the pitch before the glide physics use it. The previous tick's rotation is already stored at
	 * this point, so the camera interpolates smoothly to the new pitch.
	 */
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void elytra$beforeMovement(final CallbackInfo ci) {
		ElytraClient.flight().beforeMovement((LocalPlayer) (Object) this);
	}
}
