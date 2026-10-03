package com.urntt.elytra.mixin;

import com.urntt.elytra.ElytraClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Pose;
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

	/**
	 * The server also sends the local player the gliding pose it computes. With Keep Pose While Gliding, recompute the
	 * local pose at once, so the hitbox and the camera height never switch to the gliding pose in between.
	 */
	@Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
	private void elytra$keepPoseAfterServerUpdate(final EntityDataAccessor<?> accessor, final CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		if (player.getPose() == Pose.FALL_FLYING && ElytraClient.glide().keepsPose(player)) {
			((PlayerInvoker) player).elytra$updatePlayerPose();
		}
	}
}
