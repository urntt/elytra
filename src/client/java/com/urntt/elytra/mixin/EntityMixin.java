package com.urntt.elytra.mixin;

import com.urntt.elytra.ElytraClient;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/**
	 * Tells the glide controller when an update of the local player's synchronized data from the server has been
	 * applied, so a glide waiting for the server's end of the previous one starts before the player moves again.
	 */
	@Inject(method = "onSyncedDataUpdated(Ljava/util/List;)V", at = @At("TAIL"))
	private void elytra$afterServerUpdate(final List<SynchedEntityData.DataValue<?>> values, final CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) {
			ElytraClient.glide().afterServerUpdate(player);
		}
	}
}
