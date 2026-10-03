package com.urntt.elytra.flight;

import com.urntt.elytra.Feature;
import com.urntt.elytra.FeatureController;
import com.urntt.elytra.inventory.ElytraEquipment;
import com.urntt.elytra.mixin.EntityInvoker;
import java.util.function.BooleanSupplier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Owns whether the local player glides, on top of the fall flying flag the server synchronizes.
 *
 * <p>The server decides whether a player glides: the client asks with a "start fall flying" command, and the server
 * stops the glide when the player lands, loses the elytra, or sends the command again while gliding. Fake Elytra and
 * Ground Glide need the client to keep gliding when the server would not, so this class keeps a client-side glide
 * that {@code LivingEntity.isFallFlying()} reports in addition to the server's flag. The server does not know about
 * such a glide. Everything else (starting, stopping, Instant Fly, Stop Flying in Water) goes through the vanilla
 * command.
 */
public final class GlideController {
	/** Horizontal speed, in blocks per tick, below which a glide on the ground has come to rest and ends. */
	private static final double MIN_GROUND_GLIDE_SPEED = 0.05;
	/** Ticks between two attempts to hand a client-side glide back to the server. */
	private static final int RESYNC_INTERVAL_TICKS = 20;
	/** Index of the fall flying bit in the entity's shared flags ({@code Entity.FLAG_FALL_FLYING}, which is protected). */
	private static final int FLAG_FALL_FLYING = 7;

	private final FeatureController features;
	private final ElytraEquipment equipment;
	private @Nullable LocalPlayer player;
	private boolean clientGliding;
	/** Set by a jump off the ground; Instant Fly starts a glide as soon as it can while this jump lasts. */
	private boolean instantFlyPending;
	private int resyncCooldown;

	public GlideController(final FeatureController features, final ElytraEquipment equipment) {
		this.features = features;
		this.equipment = equipment;
	}

	/**
	 * Returns whether {@code player} glides only on the client, for Fake Elytra or Ground Glide.
	 */
	public boolean isClientGliding(final LocalPlayer player) {
		return this.clientGliding && this.player == player;
	}

	/**
	 * Returns whether {@code entity} is the local player and keeps the view height, hitbox, and animation of standing
	 * while it glides (Keep Pose While Gliding). Glides of every other entity look vanilla.
	 */
	public boolean keepsPose(final Entity entity) {
		return entity instanceof LocalPlayer && this.features.isActive(Feature.KEEP_POSE);
	}

	/**
	 * Returns the fall flying flag as the server last synchronized it, ignoring any client-side glide.
	 */
	public static boolean isServerGliding(final LocalPlayer player) {
		return ((EntityInvoker) player).elytra$getSharedFlag(FLAG_FALL_FLYING);
	}

	/**
	 * Replaces {@code Player.tryToStartFallFlying()} for the local player, whose result decides whether the client
	 * sends the "start fall flying" command. No Gliding refuses, Chest Swap puts on an elytra first, and Fake Elytra
	 * starts a client-side glide when there is still no elytra, without telling the server.
	 */
	public boolean tryToStartFallFlying(final LocalPlayer player, final BooleanSupplier vanilla) {
		this.bind(player);
		if (this.features.isActive(Feature.NO_GLIDING)) {
			return false;
		}
		if (!ElytraEquipment.hasUsableGlider(player) && canGlideWithoutElytra(player)) {
			this.equipment.equipElytraForGlide(player);
		}
		if (vanilla.getAsBoolean()) {
			return true;
		}
		if (this.features.isActive(Feature.FAKE_ELYTRA) && !player.isFallFlying()
				&& !ElytraEquipment.hasUsableGlider(player) && canGlideWithoutElytra(player)) {
			this.clientGliding = true;
		}
		return false;
	}

	/**
	 * Called when the local player jumps off the ground, so Instant Fly can start a glide from this jump.
	 */
	public void onJump(final LocalPlayer player) {
		this.bind(player);
		this.instantFlyPending = true;
	}

	/**
	 * Ends the current glide: the client-side one at once, and the server's by sending the "start fall flying"
	 * command, which the server answers by stopping a glide in progress.
	 */
	public void stopGliding(final LocalPlayer player) {
		this.bind(player);
		this.clientGliding = false;
		if (isServerGliding(player)) {
			sendStartFallFlying(player);
			player.stopFallFlying();
		}
	}

	/**
	 * Called at the end of every client tick, after the player moved and its position was sent to the server.
	 */
	public void tick(final LocalPlayer player) {
		this.bind(player);
		boolean serverGliding = isServerGliding(player);

		// The server stops a glide as soon as it sees the player on the ground; keep it going on the client.
		if (serverGliding && player.onGround() && this.features.isActive(Feature.GROUND_GLIDE)) {
			this.clientGliding = true;
		}
		if (this.clientGliding && !this.canKeepClientGliding(player)) {
			this.clientGliding = false;
		}

		if (player.isFallFlying() && player.isInWater() && this.features.isActive(Feature.STOP_IN_WATER)) {
			this.stopGliding(player);
		}

		this.tickInstantFly(player);

		// With a usable elytra, hand a client-side glide back to the server once the player is in the air again, so the
		// server treats the flight as gliding (no fall damage, elytra durability) instead of as falling.
		if (this.resyncCooldown > 0) {
			this.resyncCooldown--;
		}
		if (this.clientGliding && !serverGliding && this.resyncCooldown == 0 && !player.onGround() && !player.isInLiquid()
				&& ElytraEquipment.hasUsableGlider(player)) {
			sendStartFallFlying(player);
			this.resyncCooldown = RESYNC_INTERVAL_TICKS;
		}
	}

	/**
	 * Starts a glide for a pending jump. The jump's position update was sent this tick, so the server already sees the
	 * player off the ground.
	 *
	 * <p>A jump on landing from a glide happens before the server's end of that glide reaches the client, so the
	 * player still counts as gliding then. Sending the command at that point would only end the old glide, so the jump
	 * stays pending until the server's update arrives, and a new glide starts right after. The pending jump ends when
	 * the player is back on the ground.
	 */
	private void tickInstantFly(final LocalPlayer player) {
		if (!this.instantFlyPending) {
			return;
		}
		if (!this.features.isActive(Feature.INSTANT_FLY) || player.onGround() || player.onClimbable()) {
			this.instantFlyPending = false;
		} else if (!player.isFallFlying()) {
			this.instantFlyPending = false;
			if (player.tryToStartFallFlying()) {
				sendStartFallFlying(player);
			}
		}
	}

	private boolean canKeepClientGliding(final LocalPlayer player) {
		if (!player.isAlive() || player.isPassenger() || player.hasEffect(MobEffects.LEVITATION)
				|| player.getAbilities().flying || player.onClimbable()) {
			return false;
		}
		if (player.onGround() && (!this.features.isActive(Feature.GROUND_GLIDE)
				|| player.getDeltaMovement().horizontalDistance() < MIN_GROUND_GLIDE_SPEED)) {
			return false;
		}
		return ElytraEquipment.hasUsableGlider(player) || this.features.isActive(Feature.FAKE_ELYTRA);
	}

	/**
	 * Vanilla's conditions for starting a glide, except for wearing an elytra.
	 */
	private static boolean canGlideWithoutElytra(final LocalPlayer player) {
		return !player.isFallFlying() && !player.onGround() && !player.isPassenger()
				&& !player.hasEffect(MobEffects.LEVITATION) && !player.getAbilities().flying && !player.isInLiquid();
	}

	private static void sendStartFallFlying(final LocalPlayer player) {
		player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
	}

	private void bind(final LocalPlayer player) {
		if (this.player != player) {
			this.player = player;
			this.clientGliding = false;
			this.instantFlyPending = false;
			this.resyncCooldown = 0;
		}
	}
}
