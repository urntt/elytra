package com.urntt.elytra.gametest;

import static com.urntt.elytra.gametest.GameTestSupport.LOGGER;
import static com.urntt.elytra.gametest.GameTestSupport.bindKey;
import static com.urntt.elytra.gametest.GameTestSupport.check;
import static com.urntt.elytra.gametest.GameTestSupport.configure;
import static com.urntt.elytra.gametest.GameTestSupport.onlyEnable;
import static com.urntt.elytra.gametest.GameTestSupport.unbindKey;

import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import com.urntt.elytra.config.Tuning;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Checks the features that steer a glide in singleplayer: Fully and Partially Controlled Flying, No Crash, Elytra
 * Boost, and Autopilot. Glides start {@value #HEIGHT} blocks above the ground, facing south (+Z).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ElytraFlightGameTest implements FabricClientGameTest {
	private static final double HEIGHT = 60.0;
	private static final double AUTOPILOT_HEIGHT = 200.0;
	private static final int AUTOPILOT_TICKS = 1000;
	private static final double EPSILON = 1.0E-3;

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			FlightLab lab = new FlightLab(context, singleplayer.getServer());

			checkFullyControlled(context, lab);
			checkPartiallyControlled(context, lab);
			checkNoCrashAtWall(context, lab);
			checkNoCrashAtUnloadedChunk(context, lab);
			checkElytraBoost(context, lab);
			checkAutopilot(context, lab);
		}
		onlyEnable(context);
	}

	/**
	 * Without input the player hovers; the movement keys move it at exactly the configured speeds.
	 */
	private static void checkFullyControlled(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context);
		startGlide(lab, 0, 0);
		context.waitTicks(10);
		double vanillaSpeed = lab.velocity().length();

		onlyEnable(context, Feature.FULLY_CONTROLLED);
		startGlide(lab, 0, 0);
		context.waitTicks(3);
		Vec3 hoverStart = lab.position();
		context.waitTicks(20);
		double drift = lab.position().distanceTo(hoverStart);
		LOGGER.info("Speed 10 ticks into a glide: vanilla {}; drift over 20 ticks without input: {}", vanillaSpeed, drift);
		check(vanillaSpeed > 0.1, "a vanilla glide should be moving");
		check(drift < EPSILON && lab.gliding(), "Fully Controlled Flying should hover without input");

		Vec3 forward = holdAndMeasure(context, lab, options -> options.keyUp, 5);
		check(Math.abs(forward.z - Tuning.FULLY_CONTROLLED_HORIZONTAL_SPEED.defaultValue()) < EPSILON
						&& Math.abs(forward.x) < EPSILON && Math.abs(forward.y) < EPSILON,
				"forward should move south at the horizontal speed, got " + forward);
		Vec3 left = holdAndMeasure(context, lab, options -> options.keyLeft, 5);
		check(Math.abs(left.x - Tuning.FULLY_CONTROLLED_HORIZONTAL_SPEED.defaultValue()) < EPSILON,
				"left should move east when facing south, got " + left);
		Vec3 up = holdAndMeasure(context, lab, options -> options.keyJump, 5);
		check(Math.abs(up.y - Tuning.FULLY_CONTROLLED_VERTICAL_SPEED.defaultValue()) < EPSILON
						&& up.horizontalDistance() < EPSILON,
				"jump should move up at the vertical speed, got " + up);
		Vec3 down = holdAndMeasure(context, lab, options -> options.keyShift, 5);
		check(Math.abs(down.y + Tuning.FULLY_CONTROLLED_VERTICAL_SPEED.defaultValue()) < EPSILON,
				"sneak should move down at the vertical speed, got " + down);
		LOGGER.info("Fully Controlled Flying moved by {} forward, {} left, {} up, {} down per tick", forward, left, up, down);
	}

	/**
	 * Forward and jump add to vanilla's glide physics, the natural descent can be turned off, and the speed is capped.
	 */
	private static void checkPartiallyControlled(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context);
		double vanillaForward = glideHolding(context, lab, options -> options.keyUp, 30).horizontalDistance();
		double vanillaUp = glideHolding(context, lab, options -> options.keyJump, 10).y;
		double vanillaIdle = glideHolding(context, lab, null, 30).y;

		onlyEnable(context, Feature.PARTIALLY_CONTROLLED);
		double forward = glideHolding(context, lab, options -> options.keyUp, 30).horizontalDistance();
		double up = glideHolding(context, lab, options -> options.keyJump, 10).y;
		LOGGER.info("Partially Controlled Flying: speed after 30 ticks of forward {} (vanilla {}), vertical speed after 10"
				+ " ticks of jump {} (vanilla {})", forward, vanillaForward, up, vanillaUp);
		check(forward > vanillaForward + 0.5, "forward should speed the glide up");
		check(up > vanillaUp + 0.3, "jump should push the glide up");

		configure(context, config -> config.set(Tuning.PARTIALLY_CONTROLLED_DESCENT_SCALE, 0.0));
		double idle = glideHolding(context, lab, null, 30).y;
		LOGGER.info("Vertical speed after 30 ticks without input: natural descent 0% {}, vanilla {}", idle, vanillaIdle);
		check(Math.abs(idle) < 0.01 && vanillaIdle < -0.05, "without natural descent the glide should not sink");

		configure(context, config -> config.set(Tuning.PARTIALLY_CONTROLLED_MAX_SPEED, 0.5));
		double capped = glideHolding(context, lab, options -> options.keyUp, 30).length();
		LOGGER.info("Speed after 30 ticks of forward with a 0.5 maximum: {}", capped);
		check(capped <= 0.5 + EPSILON, "the maximum speed should cap the glide");
	}

	/**
	 * Flies at a wall with Fully Controlled Flying at 2 blocks per tick. Without No Crash the player hits it; with No
	 * Crash it slows down and stops just in front of it.
	 */
	private static void checkNoCrashAtWall(final ClientGameTestContext context, final FlightLab lab) {
		int wallZ = 40;
		int y = (int) Math.floor(lab.groundY);
		lab.server.runCommand("fill -15 %d %d 15 %d %d minecraft:stone".formatted(y, wallZ, y + (int) HEIGHT + 20, wallZ));
		for (boolean enabled : new boolean[] {false, true}) {
			if (enabled) {
				onlyEnable(context, Feature.FULLY_CONTROLLED, Feature.NO_CRASH);
			} else {
				onlyEnable(context, Feature.FULLY_CONTROLLED);
			}
			configure(context, config -> config.set(Tuning.FULLY_CONTROLLED_HORIZONTAL_SPEED, 2.0));
			startGlide(lab, 0, 0);
			context.getInput().holdKey(options -> options.keyUp);
			int collisions = 0;
			for (int tick = 0; tick < 40; tick++) {
				context.waitTick();
				if (lab.client(client -> client.player.horizontalCollision)) {
					collisions++;
				}
			}
			context.getInput().releaseKey(options -> options.keyUp);
			double gap = wallZ - lab.client(client -> client.player.getBoundingBox().maxZ);
			LOGGER.info("Flying at a wall with No Crash {}: {} ticks with a collision, gap {}", enabled ? "on" : "off",
					collisions, gap);
			if (enabled) {
				check(collisions == 0, "No Crash should keep the player from touching the wall");
				check(gap > 0.0 && gap < 0.2, "No Crash should stop the player just in front of the wall, gap " + gap);
				context.takeScreenshot("elytra-no-crash-wall");
			} else {
				check(collisions > 0, "without No Crash the player should run into the wall");
			}
		}
		lab.server.runCommand("fill -15 %d %d 15 %d %d minecraft:air".formatted(y, wallZ, y + (int) HEIGHT + 20, wallZ));
	}

	/**
	 * Removes a column of chunks on the client only, as if it had not loaded yet, and flies at it.
	 */
	private static void checkNoCrashAtUnloadedChunk(final ClientGameTestContext context, final FlightLab lab) {
		int chunkZ = 3;
		double boundary = SectionPos.sectionToBlockCoord(chunkZ);
		for (boolean enabled : new boolean[] {true, false}) {
			if (enabled) {
				onlyEnable(context, Feature.FULLY_CONTROLLED, Feature.NO_CRASH);
			} else {
				onlyEnable(context, Feature.FULLY_CONTROLLED);
			}
			startGlide(lab, 8, 0);
			context.runOnClient(client -> {
				for (int chunkX = -2; chunkX <= 2; chunkX++) {
					client.level.getChunkSource().drop(new ChunkPos(chunkX, chunkZ));
				}
			});
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(80);
			context.getInput().releaseKey(options -> options.keyUp);
			double front = lab.client(client -> client.player.getBoundingBox().maxZ);
			LOGGER.info("Flying at an unloaded chunk starting at z={} with No Crash {}: front of the player at z={}", boundary,
					enabled ? "on" : "off", front);
			if (enabled) {
				check(front < boundary && front > boundary - 0.2, "No Crash should stop in front of the unloaded chunk");
			} else {
				check(front > boundary, "without No Crash the player should fly into the unloaded chunk");
			}
		}
	}

	/**
	 * The boost key pushes the glide like a rocket for the configured duration, then removes the client-side rocket.
	 */
	private static void checkElytraBoost(final ClientGameTestContext context, final FlightLab lab) {
		KeyMapping boostKey = bindKey(context, ElytraClient.BOOST_KEY_NAME, "key.keyboard.j");
		onlyEnable(context);
		startGlide(lab, 0, 0);
		context.getInput().pressKey(boostKey);
		context.waitTicks(15);
		double vanillaSpeed = lab.velocity().length();
		context.takeScreenshot("elytra-boost-off");

		onlyEnable(context, Feature.ELYTRA_BOOST);
		startGlide(lab, 0, 0);
		context.getInput().pressKey(boostKey);
		context.waitTicks(15);
		double boostedSpeed = lab.velocity().length();
		LOGGER.info("Speed 15 ticks after the boost key: off {}, on {}", vanillaSpeed, boostedSpeed);
		check(boostedSpeed > vanillaSpeed + 0.8, "Elytra Boost should push the glide");

		int duration = (int) Tuning.ELYTRA_BOOST_DURATION.defaultValue();
		context.waitTicks(duration);
		long rockets = lab.client(client -> StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
				.filter(FireworkRocketEntity.class::isInstance)
				.count());
		int tracked = lab.client(client -> ElytraClient.boost().activeRockets());
		check(tracked == 0 && rockets == 0, "the rocket should be removed after its duration");
		unbindKey(context, boostKey);
	}

	/**
	 * Autopilot keeps a glide going for {@value #AUTOPILOT_TICKS} ticks without losing much height. A vanilla level
	 * glide loses about 150 blocks in that time. Starting slowly, Autopilot first dives about 55 blocks to gain speed,
	 * so this glide starts higher.
	 */
	private static void checkAutopilot(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context, Feature.AUTOPILOT);
		startGlide(lab, 0, 0, AUTOPILOT_HEIGHT);
		double start = lab.height();
		double lowest = start;
		double highest = start;
		for (int tick = 1; tick <= AUTOPILOT_TICKS; tick++) {
			context.waitTick();
			double height = lab.height();
			lowest = Math.min(lowest, height);
			highest = Math.max(highest, height);
			check(lab.gliding(), "Autopilot should keep gliding");
			if (tick % 100 == 0) {
				LOGGER.info("Autopilot after {} ticks: {} blocks above the start", tick, height - start);
			}
		}
		double end = lab.height();
		LOGGER.info("Autopilot over {} ticks: lowest {}, highest {}, end {} blocks relative to the start", AUTOPILOT_TICKS,
				lowest - start, highest - start, end - start);
		check(lowest > start - 70, "Autopilot should not lose much height");
		check(end > start - 30, "Autopilot should have climbed back by the end");
	}

	/**
	 * Puts the player into a glide {@value #HEIGHT} blocks above {@code (x, z)}, facing south and level, wearing an
	 * elytra.
	 */
	private static void startGlide(final FlightLab lab, final double x, final double z) {
		startGlide(lab, x, z, HEIGHT);
	}

	private static void startGlide(final FlightLab lab, final double x, final double z, final double height) {
		lab.resetPlayer(x, z);
		lab.wear("minecraft:elytra");
		lab.launchGlide(x, height, z, 0.0F, 0.0F);
	}

	/**
	 * Holds a key for {@code ticks} ticks and returns the movement of the last tick.
	 */
	private static Vec3 holdAndMeasure(final ClientGameTestContext context, final FlightLab lab,
			final Function<Options, KeyMapping> key, final int ticks) {
		context.getInput().holdKey(key);
		context.waitTicks(ticks - 1);
		Vec3 before = lab.position();
		context.waitTick();
		Vec3 movement = lab.position().subtract(before);
		context.getInput().releaseKey(key);
		context.waitTicks(2);
		return movement;
	}

	/**
	 * Starts a fresh glide, holds {@code key} (or nothing) for {@code ticks} ticks, and returns the velocity.
	 */
	private static Vec3 glideHolding(final ClientGameTestContext context, final FlightLab lab,
			final Function<Options, KeyMapping> key, final int ticks) {
		startGlide(lab, 0, 0);
		if (key != null) {
			context.getInput().holdKey(key);
		}
		context.waitTicks(ticks);
		Vec3 velocity = lab.velocity();
		if (key != null) {
			context.getInput().releaseKey(key);
		}
		return velocity;
	}
}
