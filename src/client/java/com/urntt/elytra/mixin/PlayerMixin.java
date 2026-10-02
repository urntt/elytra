package com.urntt.elytra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.urntt.elytra.ElytraClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * Routes the local player's attempts to start gliding through No Gliding, Chest Swap, and Fake Elytra. The result
	 * still decides whether the client asks the server to start the glide. Other players keep vanilla behavior.
	 */
	@WrapMethod(method = "tryToStartFallFlying")
	private boolean elytra$tryToStartFallFlying(final Operation<Boolean> original) {
		if ((Object) this instanceof LocalPlayer player) {
			return ElytraClient.glide().tryToStartFallFlying(player, original::call);
		}
		return original.call();
	}
}
