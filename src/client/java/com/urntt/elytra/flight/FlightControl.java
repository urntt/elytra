package com.urntt.elytra.flight;

import com.urntt.elytra.Feature;
import com.urntt.elytra.FeatureController;
import com.urntt.elytra.config.ElytraConfig;
import com.urntt.elytra.config.Tuning;
import java.util.function.Supplier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Steers the local player's glide: Fully Controlled Flying, Partially Controlled Flying, and Autopilot.
 */
public final class FlightControl {
	// Chosen by simulating vanilla's glide physics: from any starting speed, these values gain height over time.
	// Starting slowly costs up to about 55 blocks of height before the first climb.

	/** Pitch, in degrees below the horizon, at which Autopilot dives to gain speed. */
	static final float AUTOPILOT_DIVE_PITCH = 35.0F;
	/** Pitch, in degrees above the horizon, at which Autopilot climbs to trade speed for height. */
	static final float AUTOPILOT_CLIMB_PITCH = 45.0F;
	/** Speed, in blocks per tick, at which Autopilot stops diving and starts climbing. */
	static final double AUTOPILOT_CLIMB_AT_SPEED = 2.0;
	/** Speed, in blocks per tick, at which Autopilot stops climbing and starts diving. */
	static final double AUTOPILOT_DIVE_AT_SPEED = 0.7;
	/** Largest pitch change Autopilot makes per tick, in degrees, so the camera turns smoothly. */
	static final float AUTOPILOT_MAX_PITCH_CHANGE = 10.0F;

	private final FeatureController features;
	private final ElytraConfig config;
	private @Nullable LocalPlayer autopilotPlayer;
	private boolean autopilotDiving = true;

	public FlightControl(final FeatureController features, final ElytraConfig config) {
		this.features = features;
		this.config = config;
	}

	/**
	 * Computes the local player's glide velocity for this tick, replacing vanilla's glide physics with Fully
	 * Controlled Flying, or adding the key input of Partially Controlled Flying to them.
	 *
	 * @param vanilla vanilla's glide physics for this tick
	 */
	public Vec3 glideMovement(final LocalPlayer player, final Supplier<Vec3> vanilla) {
		if (this.features.isActive(Feature.FULLY_CONTROLLED)) {
			return this.fullyControlledMovement(player);
		}
		if (this.features.isActive(Feature.PARTIALLY_CONTROLLED)) {
			return this.partiallyControlledMovement(player, vanilla.get());
		}
		return vanilla.get();
	}

	/**
	 * Multiplier of the gravity in vanilla's glide physics (Partially Controlled Flying's natural descent).
	 */
	public double gravityScale() {
		return this.features.isActive(Feature.PARTIALLY_CONTROLLED)
				? this.config.get(Tuning.PARTIALLY_CONTROLLED_DESCENT_SCALE)
				: 1.0;
	}

	/**
	 * Multiplier of the speed vanilla's glide physics gain by turning height into forward speed (Partially Controlled
	 * Flying's natural acceleration).
	 */
	public double accelerationScale() {
		return this.features.isActive(Feature.PARTIALLY_CONTROLLED)
				? this.config.get(Tuning.PARTIALLY_CONTROLLED_ACCELERATION_SCALE)
				: 1.0;
	}

	/**
	 * Called at the start of the local player's movement each tick. Autopilot sets the pitch the glide physics use
	 * this tick: it dives until the glide is fast, then climbs until it is slow, which gains height over time.
	 */
	public void beforeMovement(final LocalPlayer player) {
		if (this.autopilotPlayer != player || !player.isFallFlying()) {
			this.autopilotPlayer = player;
			this.autopilotDiving = true;
			return;
		}
		if (!this.features.isActive(Feature.AUTOPILOT)) {
			return;
		}

		double speed = player.getDeltaMovement().length();
		if (this.autopilotDiving && speed >= AUTOPILOT_CLIMB_AT_SPEED) {
			this.autopilotDiving = false;
		} else if (!this.autopilotDiving && speed <= AUTOPILOT_DIVE_AT_SPEED) {
			this.autopilotDiving = true;
		}
		float target = this.autopilotDiving ? AUTOPILOT_DIVE_PITCH : -AUTOPILOT_CLIMB_PITCH;
		float change = Mth.clamp(target - player.getXRot(), -AUTOPILOT_MAX_PITCH_CHANGE, AUTOPILOT_MAX_PITCH_CHANGE);
		player.setXRot(player.getXRot() + change);
	}

	/**
	 * Moves only as the movement keys say: horizontally relative to where the player faces, up with jump, down with
	 * sneak, and not at all without input.
	 */
	private Vec3 fullyControlledMovement(final LocalPlayer player) {
		Input input = player.input.keyPresses;
		double forward = axis(input.forward(), input.backward());
		double strafe = axis(input.left(), input.right());
		double vertical = axis(input.jump(), input.shift()) * this.config.get(Tuning.FULLY_CONTROLLED_VERTICAL_SPEED);

		Vec3 horizontal = Vec3.ZERO;
		if (forward != 0.0 || strafe != 0.0) {
			// The same rotation vanilla applies to movement input (Entity.getInputVector).
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			double sin = Mth.sin(yaw);
			double cos = Mth.cos(yaw);
			horizontal = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin)
					.normalize()
					.scale(this.config.get(Tuning.FULLY_CONTROLLED_HORIZONTAL_SPEED));
		}
		return new Vec3(horizontal.x, vertical, horizontal.z);
	}

	/**
	 * Adds key input to vanilla's glide physics: forward speeds up along the player's facing, back slows down (but
	 * does not reverse), jump and sneak push up and down. The result is limited to the maximum speed.
	 */
	private Vec3 partiallyControlledMovement(final LocalPlayer player, final Vec3 movement) {
		Input input = player.input.keyPresses;
		double acceleration = this.config.get(Tuning.PARTIALLY_CONTROLLED_HORIZONTAL_ACCELERATION);
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));

		Vec3 result = movement;
		if (input.forward()) {
			result = result.add(facing.scale(acceleration));
		}
		if (input.backward()) {
			double along = result.x * facing.x + result.z * facing.z;
			if (along > 0.0) {
				result = result.subtract(facing.scale(Math.min(acceleration, along)));
			}
		}
		if (input.jump()) {
			result = result.add(0.0, this.config.get(Tuning.PARTIALLY_CONTROLLED_ASCEND_ACCELERATION), 0.0);
		}
		if (input.shift()) {
			result = result.subtract(0.0, this.config.get(Tuning.PARTIALLY_CONTROLLED_DESCEND_ACCELERATION), 0.0);
		}

		double maxSpeed = this.config.get(Tuning.PARTIALLY_CONTROLLED_MAX_SPEED);
		double speed = result.length();
		return speed > maxSpeed ? result.scale(maxSpeed / speed) : result;
	}

	private static double axis(final boolean positive, final boolean negative) {
		return (positive ? 1.0 : 0.0) - (negative ? 1.0 : 0.0);
	}
}
