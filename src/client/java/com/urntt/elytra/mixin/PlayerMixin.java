package com.urntt.elytra.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.urntt.elytra.ElytraClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * Routes the local player's attempts to start gliding through No Gliding, Instant Landing, Chest Swap, and Fake
	 * Elytra. The result still decides whether the client asks the server to start the glide. Other players keep
	 * vanilla behavior.
	 */
	@WrapMethod(method = "tryToStartFallFlying")
	private boolean elytra$tryToStartFallFlying(final Operation<Boolean> original) {
		if ((Object) this instanceof LocalPlayer player) {
			return ElytraClient.glide().tryToStartFallFlying(player, original::call);
		}
		return original.call();
	}

	/**
	 * Keep Pose While Gliding: the local player stands instead of taking the gliding pose, which keeps the standing
	 * hitbox and eye height. The pose still yields to crouching or crawling where standing does not fit.
	 */
	@ModifyReturnValue(method = "getDesiredPose", at = @At("RETURN"))
	private Pose elytra$keepPose(final Pose pose) {
		return pose == Pose.FALL_FLYING && ElytraClient.glide().keepsPose((Player) (Object) this) ? Pose.STANDING : pose;
	}
}
