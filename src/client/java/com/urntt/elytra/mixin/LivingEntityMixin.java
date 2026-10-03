package com.urntt.elytra.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.urntt.elytra.ElytraClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the local player's gliding. Every handler checks for {@link LocalPlayer} first, so other entities, including
 * other players and the integrated server's copy of the local player, keep vanilla behavior.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/**
	 * Reports a client-side glide (Fake Elytra, Ground Glide) in addition to the server's fall flying flag, and hides
	 * a server glide the player landed from with Instant Landing, so the glide physics, pose, camera, and sounds
	 * follow the client's view of the glide.
	 */
	@ModifyReturnValue(method = "isFallFlying", at = @At("RETURN"))
	private boolean elytra$followClientGlide(final boolean fallFlying) {
		if ((Object) this instanceof LocalPlayer player) {
			return ElytraClient.glide().isFallFlying(player, fallFlying);
		}
		return fallFlying;
	}

	/**
	 * Lets Fully Controlled and Partially Controlled Flying replace or adjust the glide velocity vanilla computes.
	 */
	@WrapOperation(method = "travelFallFlying", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;updateFallFlyingMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 elytra$controlGlide(final LivingEntity self, final Vec3 movement, final Operation<Vec3> original) {
		if (self instanceof LocalPlayer player) {
			return ElytraClient.flight().glideMovement(player, () -> original.call(self, movement));
		}
		return original.call(self, movement);
	}

	/**
	 * Lets No Crash shorten the glide's movement before it runs into a wall or an unloaded chunk.
	 */
	@WrapOperation(method = "travelFallFlying", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
	private void elytra$avoidCrash(final LivingEntity self, final MoverType moverType, final Vec3 movement,
			final Operation<Void> original) {
		Vec3 allowed = movement;
		if (self instanceof LocalPlayer player) {
			allowed = ElytraClient.crashGuard().limit(player, movement);
			if (allowed != movement) {
				self.setDeltaMovement(allowed);
			}
		}
		original.call(self, moverType, allowed);
	}

	/**
	 * Lets Instant Landing end the glide on the client as soon as it touches the ground.
	 */
	@Inject(method = "travelFallFlying", at = @At("TAIL"))
	private void elytra$onGlideMoved(final Vec3 input, final CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) {
			ElytraClient.glide().onGlideMoved(player);
		}
	}

	/**
	 * Scales the gravity of the glide physics (Partially Controlled Flying's natural descent).
	 */
	@ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getEffectiveGravity()D"))
	private double elytra$scaleGlideGravity(final double gravity) {
		return (Object) this instanceof LocalPlayer ? gravity * ElytraClient.flight().gravityScale() : gravity;
	}

	/**
	 * Scales how much falling speed the glide physics turn into forward speed (Partially Controlled Flying's natural
	 * acceleration). The constant is the factor in {@code convert = movement.y * -0.1 * liftForce}.
	 */
	@ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "CONSTANT", args = "doubleValue=-0.1"))
	private double elytra$scaleGlideAcceleration(final double factor) {
		return (Object) this instanceof LocalPlayer ? factor * ElytraClient.flight().accelerationScale() : factor;
	}

	/**
	 * Tells Instant Fly that the local player jumped off the ground.
	 */
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void elytra$onJump(final CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) {
			ElytraClient.glide().onJump(player);
		}
	}
}
