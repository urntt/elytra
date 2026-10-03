package com.urntt.elytra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.urntt.elytra.ElytraClient;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {
	/**
	 * Keep Pose While Gliding: renders the local player's body and limbs as when not gliding.
	 */
	@WrapOperation(method = "extractHumanoidRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
	private static boolean elytra$hideGlideAnimation(final LivingEntity entity, final Operation<Boolean> original) {
		return original.call(entity) && !ElytraClient.glide().keepsPose(entity);
	}
}
