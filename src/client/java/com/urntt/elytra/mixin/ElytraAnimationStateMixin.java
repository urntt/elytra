package com.urntt.elytra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.urntt.elytra.ElytraClient;
import net.minecraft.world.entity.ElytraAnimationState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ElytraAnimationState.class)
public abstract class ElytraAnimationStateMixin {
	/**
	 * Keep Pose While Gliding: keeps the local player's elytra folded as when not gliding.
	 */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
	private boolean elytra$foldWings(final LivingEntity entity, final Operation<Boolean> original) {
		return original.call(entity) && !ElytraClient.glide().keepsPose(entity);
	}
}
