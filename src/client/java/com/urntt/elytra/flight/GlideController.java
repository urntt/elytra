package com.urntt.elytra.flight;

import com.urntt.elytra.Feature;
import com.urntt.elytra.FeatureController;
import com.urntt.elytra.inventory.ElytraEquipment;
import com.urntt.elytra.mixin.EntityInvoker;
import com.urntt.elytra.mixin.PlayerInvoker;
import java.util.function.BooleanSupplier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
 *
 * <p>Instant Landing works the other way round: the server ends a glide when it sees the player on the ground, which
 * the client learns a round trip after landing. The client ends the glide itself on landing, and hides the server's
 * glide until the server's end of it arrives.
 *
 * <p>Starting a new glide before the server's end of the old one arrived would only end the old one, because the
 * server takes the "start fall flying" command for a request to stop a glide in progress. Such a start instead shows
 * the old glide until its end arrives, and starts the new glide the moment it does ({@link #afterServerUpdate}). If
 * the server missed the landing (the player left the ground again before the server's next tick looked), the old
 * glide simply goes on.
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
	/** Set by a jump off the ground, for Instant Fly to start a glide at the end of the tick. */
	private boolean instantFlyPending;
	private int resyncCooldown;
	/**
	 * Set when the player lands from a server glide with Instant Landing: the client hides the server's glide until the
	 * server's end of it arrives or the player starts gliding again.
	 */
	private boolean landed;
	/**
	 * The feature (Instant Fly or Instant Landing) that starts a new glide the moment the server's end of the old one
	 * arrives, unless the player is back on the ground first.
	 */
	private @Nullable Feature restartFor;

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
	 * Replaces {@code LivingEntity.isFallFlying()} for the local player, given the server's fall flying flag: a
	 * client-side glide counts as gliding, and a server glide the player landed from with Instant Landing does not.
	 */
	public boolean isFallFlying(final LocalPlayer player, final boolean serverGliding) {
		if (this.player != player) {
			return serverGliding;
		}
		return this.clientGliding || serverGliding && !this.landed;
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
	 * starts a client-side glide when there is still no elytra, without telling the server. While the server has not
	 * ended a glide the player landed from with Instant Landing, that glide shows again instead.
	 */
	public boolean tryToStartFallFlying(final LocalPlayer player, final BooleanSupplier vanilla) {
		this.bind(player);
		if (this.features.isActive(Feature.NO_GLIDING)) {
			return false;
		}
		if (this.landed) {
			if (canGlideWithoutElytra(player)) {
				// The server has not ended the glide the player landed from yet; glide on with it instead.
				this.landed = false;
				this.restartFor = Feature.INSTANT_LANDING;
			}
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
	 * Called after the glide physics moved the local player. With Instant Landing, a server glide that touches the
	 * ground ends on the client at once, before the player's pose and movement are updated for the next tick.
	 */
	public void onGlideMoved(final LocalPlayer player) {
		this.bind(player);
		if (player.onGround() && !this.clientGliding && isServerGliding(player)
				&& this.features.isActive(Feature.INSTANT_LANDING)) {
			this.landed = true;
			// Walking keeps a downward pull on the ground, which the glide's movement does not leave. Without it, the
			// next tick's movement would not reach the ground and the player would count as airborne for a tick.
			Vec3 movement = player.getDeltaMovement();
			player.setDeltaMovement(movement.x, Math.min(movement.y, -player.getGravity()), movement.z);
		}
	}

	/**
	 * Called after the client applied an update of the local player's synchronized data from the server. When it
	 * brings the server's end of a glide, the glide the player landed from no longer needs hiding, and a glide the
	 * player wants to follow it with starts at once, before the player moves a tick without gliding.
	 */
	public void afterServerUpdate(final LocalPlayer player) {
		if (this.player != player || isServerGliding(player)) {
			return;
		}
		this.landed = false;
		Feature feature = this.restartFor;
		this.restartFor = null;
		if (feature != null && this.features.isActive(feature) && !player.onClimbable() && player.tryToStartFallFlying()) {
			sendStartFallFlying(player);
			// The update also brought the server's standing pose.
			((PlayerInvoker) player).elytra$updatePlayerPose();
		}
	}

	/**
	 * Ends the current glide: the client-side one at once, and the server's by sending the "start fall flying"
	 * command, which the server answers by stopping a glide in progress.
	 */
	public void stopGliding(final LocalPlayer player) {
		this.bind(player);
		this.clientGliding = false;
		this.restartFor = null;
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
		this.tickLanding(player, serverGliding);

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
	 * Shows the server's glide again once its end arrived, and drops a pending restart when the player is back on the
	 * ground.
	 */
	private void tickLanding(final LocalPlayer player, final boolean serverGliding) {
		if (this.landed && (!serverGliding || !this.features.isActive(Feature.INSTANT_LANDING))) {
			this.landed = false;
		}
		if (this.restartFor != null && (!this.features.isActive(this.restartFor) || player.onGround()
				|| player.onClimbable())) {
			this.restartFor = null;
		}
	}

	/**
	 * Starts a glide for the jump the player made this tick. The jump's position update was sent this tick, so the
	 * server already sees the player off the ground.
	 *
	 * <p>A jump on landing from a glide happens before the server's end of that glide reaches the client, so the
	 * player still glides then. Sending the command at that point would only end the old glide, so the new glide
	 * starts the moment the server's end arrives instead. With Instant Landing, the old glide is hidden, and the
	 * attempt to start one shows it again ({@link #tryToStartFallFlying}).
	 */
	private void tickInstantFly(final LocalPlayer player) {
		if (!this.instantFlyPending) {
			return;
		}
		this.instantFlyPending = false;
		if (!this.features.isActive(Feature.INSTANT_FLY) || player.onGround() || player.onClimbable()) {
			return;
		}
		if (!player.isFallFlying()) {
			if (player.tryToStartFallFlying()) {
				sendStartFallFlying(player);
			}
		} else if (!this.clientGliding && isServerGliding(player)) {
			this.restartFor = Feature.INSTANT_FLY;
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
			this.landed = false;
			this.restartFor = null;
		}
	}
}
