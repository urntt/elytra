package com.urntt.elytra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.urntt.elytra.ElytraClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	/**
	 * Keep Pose While Gliding: the local player's cape hangs and leans as when not gliding.
	 */
	@WrapOperation(method = "extractFlightData", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Avatar;getFallFlyingTicks()I"))
	private int elytra$hideGlideTime(final Avatar entity, final Operation<Integer> original) {
		return ElytraClient.glide().keepsPose(entity) ? 0 : original.call(entity);
	}
}
