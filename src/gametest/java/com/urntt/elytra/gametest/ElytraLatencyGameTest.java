package com.urntt.elytra.gametest;

import static com.urntt.elytra.gametest.GameTestSupport.LOGGER;
import static com.urntt.elytra.gametest.GameTestSupport.check;
import static com.urntt.elytra.gametest.GameTestSupport.configure;
import static com.urntt.elytra.gametest.GameTestSupport.onlyEnable;

import com.urntt.elytra.Feature;
import com.urntt.elytra.config.MultiplayerMode;
import com.urntt.elytra.flight.GlideController;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * Checks Instant Landing, and Instant Fly along with it, on a local dedicated server whose connection to the client
 * is delayed by {@link SimulatedLatency}, and compares the landings with vanilla's over the same connection.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ElytraLatencyGameTest implements FabricClientGameTest {
	private static final String ELYTRA = "minecraft:elytra";
	/** The delay in each direction: a round trip takes 300 ms, six ticks. */
	private static final int ONE_WAY_DELAY_MILLIS = 150;
	private static final int ROUND_TRIP_TICKS = 2 * ONE_WAY_DELAY_MILLIS / 50;
	/** A longer delay for pressing jump well before the server's end of a glide arrives: a 500 ms round trip. */
	private static final int LONG_ONE_WAY_DELAY_MILLIS = 250;
	private static final int HOLD_JUMP_TICKS = 300;
	/** How often a check whose landing the server may miss is tried. */
	private static final int MAX_ATTEMPTS = 5;
	/** Jump Boost amplifier for jumps long enough to press jump again before landing: about 25 ticks in the air. */
	private static final int LONG_JUMP_BOOST = 4;
	/** Jump Boost amplifier for jumps long enough to watch a landing the server missed: about 55 ticks in the air. */
	private static final int HIGH_JUMP_BOOST = 25;

	@Override
	public void runTest(final ClientGameTestContext context) {
		onlyEnable(context);
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			FlightLab lab = new FlightLab(context, server);
			SimulatedLatency latency = SimulatedLatency.of(context);
			latency.setOneWayDelay(ONE_WAY_DELAY_MILLIS);

			checkLanding(context, lab);
			checkJumpOnLanding(context, lab);
			latency.setOneWayDelay(LONG_ONE_WAY_DELAY_MILLIS);
			checkGlideRightAfterLanding(context, lab, false);
			checkGlideRightAfterLanding(context, lab, true);
			latency.setOneWayDelay(ONE_WAY_DELAY_MILLIS);
			checkInstantFlyHoldingJump(context, lab, false);
			checkInstantFlyHoldingJump(context, lab, true);
			checkMissedLanding(context, lab, latency);
		}
		onlyEnable(context);
	}

	/**
	 * Turns exactly {@code features} on and allows the mod on the test server.
	 */
	private static void enableOnServer(final ClientGameTestContext context, final Feature... features) {
		onlyEnable(context, features);
		configure(context, config -> {
			config.setServers(List.of("localhost"));
			config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		});
	}

	private static void checkLanding(final ClientGameTestContext context, final FlightLab lab) {
		Landing vanilla = land(context, lab, false);
		Landing instant = land(context, lab, true);
		LOGGER.info("Landing with a {} ms round trip: vanilla glided on for {} ticks and slid {} blocks; Instant Landing"
						+ " glided on for {} ticks and moved {} blocks; the server's end arrived after {} and {} ticks",
				2 * ONE_WAY_DELAY_MILLIS, vanilla.glidingTicks(), format(vanilla.slide()), instant.glidingTicks(),
				format(instant.slide()), vanilla.serverEndTicks(), instant.serverEndTicks());
		check(vanilla.glidingTicks() > ROUND_TRIP_TICKS / 2,
				"vanilla should glide on until the server's end arrives, got " + vanilla.glidingTicks() + " ticks");
		check(instant.glidingTicks() == 0, "Instant Landing should stand up on landing, got " + instant.glidingTicks() + " ticks");
		check(instant.serverEndTicks() > ROUND_TRIP_TICKS / 2, "the server's end should still take a round trip");
		check(instant.slide() < vanilla.slide() / 2, "standing up should stop the slide on the ground");
	}

	/**
	 * Glides down from a few blocks up and follows the player from the first tick on the ground until the server's
	 * end of the glide reaches the client.
	 */
	private static Landing land(final ClientGameTestContext context, final FlightLab lab, final boolean instantLanding) {
		Feature[] features = instantLanding ? new Feature[] {Feature.INSTANT_LANDING} : new Feature[0];
		enableOnServer(context, features);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		lab.launchGlide(0, 6, 0, 0.0F, 25.0F);
		lab.checkServerGliding(true, "the server should glide");
		context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);

		Sample landing = sample(lab);
		check(landing.serverGliding(), "the server's end of the glide should not have arrived on landing");
		if (instantLanding) {
			check(!landing.gliding() && landing.pose() == Pose.STANDING,
					"Instant Landing should stand up on the landing tick, got " + landing);
		}
		int glidingTicks = 0;
		int ticks = 0;
		Sample current = landing;
		while (current.serverGliding()) {
			if (current.gliding() || current.pose() == Pose.FALL_FLYING) {
				glidingTicks++;
			}
			check(!instantLanding || current.onGround(), "Instant Landing should stay on the ground, got " + current);
			check(ticks++ < FlightLab.LONG_TIMEOUT, "the server should end the glide");
			context.waitTick();
			current = sample(lab);
		}
		check(!current.gliding() && current.pose() != Pose.FALL_FLYING, "the glide should be over, got " + current);
		return new Landing(glidingTicks, ticks, current.position().subtract(landing.position()).horizontalDistance());
	}

	/**
	 * Holds jump through the landing, so the player jumps again at once. Vanilla glides on in the air until the
	 * server's end arrives, Instant Landing jumps like a player without an elytra.
	 */
	private static void checkJumpOnLanding(final ClientGameTestContext context, final FlightLab lab) {
		int vanilla = glidingTicksInJumpOnLanding(context, lab, false);
		int instant = glidingTicksInJumpOnLanding(context, lab, true);
		LOGGER.info("Jumping on landing: vanilla glided for {} ticks of the jump, Instant Landing for {}", vanilla, instant);
		check(vanilla > 0, "vanilla should glide on into the jump");
		check(instant == 0, "Instant Landing should not glide in the jump, got " + instant + " ticks");
	}

	private static int glidingTicksInJumpOnLanding(final ClientGameTestContext context, final FlightLab lab,
			final boolean instantLanding) {
		enableOnServer(context, instantLanding ? new Feature[] {Feature.INSTANT_LANDING} : new Feature[0]);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		lab.launchGlide(0, 6, 0, 0.0F, 25.0F);
		context.getInput().holdKey(options -> options.keyJump);
		context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);
		context.waitFor(client -> !client.player.onGround(), FlightLab.SHORT_TIMEOUT);
		context.getInput().releaseKey(options -> options.keyJump);
		int glidingTicks = 0;
		while (!lab.client(client -> client.player.onGround())) {
			if (lab.gliding()) {
				glidingTicks++;
			}
			context.waitTick();
		}
		context.waitFor(client -> !client.player.isFallFlying(), FlightLab.LONG_TIMEOUT);
		return glidingTicks;
	}

	/**
	 * Jumps right after landing and presses jump again in the air before the server's end of the old glide arrives.
	 * Vanilla still glides then and ignores the press. Instant Landing glides on with the old glide at once, and
	 * starts a new glide without a break the moment the server's end arrives.
	 */
	private static void checkGlideRightAfterLanding(final ClientGameTestContext context, final FlightLab lab,
			final boolean instantLanding) {
		String situation = instantLanding ? "with Instant Landing" : "in vanilla";
		boolean glidedAgain = untilServerSeesLanding("pressing jump right after landing " + situation,
				() -> serverGlidesAgainAfterLanding(context, lab, instantLanding));
		LOGGER.info("Pressing jump in the air right after landing, {}: the server glided again: {}", situation, glidedAgain);
		check(glidedAgain == instantLanding, instantLanding
				? "Instant Landing should start the new glide once the server ended the old one"
				: "vanilla should ignore the press while the old glide lasts on the client");
	}

	/**
	 * Returns whether the server glided again after ending the glide the player landed from, or nothing if the server
	 * missed the landing.
	 */
	private static Optional<Boolean> serverGlidesAgainAfterLanding(final ClientGameTestContext context,
			final FlightLab lab, final boolean instantLanding) {
		enableOnServer(context, instantLanding ? new Feature[] {Feature.INSTANT_LANDING} : new Feature[0]);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		lab.server.runCommand("effect give @a minecraft:jump_boost infinite " + LONG_JUMP_BOOST + " true");
		lab.launchGlide(0, 6, 0, 0.0F, 25.0F);
		context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);
		// A tick on the ground first, so that the server rarely misses the landing.
		context.waitTick();
		lab.pressJump();
		context.waitTick();
		check(!lab.client(client -> client.player.onGround()), "the player should have jumped");
		check(lab.client(client -> GlideController.isServerGliding(client.player)),
				"the server's end of the glide should not have arrived yet");
		lab.pressJump();
		if (instantLanding) {
			check(lab.gliding(), "Instant Landing should glide on at once");
		}

		boolean serverEnded = false;
		boolean serverGlidedAgain = false;
		while (!lab.client(client -> client.player.onGround())) {
			check(!instantLanding || lab.gliding(), "Instant Landing should glide without a break");
			boolean serverGliding = lab.serverGliding();
			serverEnded |= !serverGliding;
			serverGlidedAgain |= serverEnded && serverGliding;
			context.waitTick();
		}
		context.waitFor(client -> !client.player.isFallFlying(), FlightLab.LONG_TIMEOUT);
		return serverEnded ? Optional.of(serverGlidedAgain) : Optional.empty();
	}

	/**
	 * Repeats a check until the server sees its landing. A jump right after landing sometimes reaches the server
	 * within the same server tick as the landing, so that the server misses the landing and glides on, which
	 * {@link #checkMissedLanding} covers.
	 */
	private static <T> T untilServerSeesLanding(final String check, final Supplier<Optional<T>> attempt) {
		for (int attempts = 1; attempts <= MAX_ATTEMPTS; attempts++) {
			Optional<T> result = attempt.get();
			if (result.isPresent()) {
				return result.get();
			}
			LOGGER.info("{}: the server missed the landing in attempt {}", check, attempts);
		}
		throw new AssertionError(check + ": the server missed the landing in all " + MAX_ATTEMPTS + " attempts");
	}

	/**
	 * Holds jump and forward with Instant Fly. Every jump should glide, and once it glides, it should glide without a
	 * break until it lands, also when the server's end of the glide before it arrives.
	 */
	private static void checkInstantFlyHoldingJump(final ClientGameTestContext context, final FlightLab lab,
			final boolean instantLanding) {
		enableOnServer(context, instantLanding
				? new Feature[] {Feature.INSTANT_FLY, Feature.INSTANT_LANDING}
				: new Feature[] {Feature.INSTANT_FLY});
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		context.getInput().holdKey(options -> options.keyJump);
		context.getInput().holdKey(options -> options.keyUp);

		// For each time in the air, whether the player glided, and the number of breaks in the glides.
		List<Boolean> glides = new ArrayList<>();
		int breaks = 0;
		boolean wasOnGround = true;
		boolean wasGliding = false;
		boolean glided = false;
		for (int tick = 0; tick < HOLD_JUMP_TICKS; tick++) {
			context.waitTick();
			boolean onGround = lab.client(client -> client.player.onGround());
			boolean gliding = lab.gliding();
			if (!onGround) {
				glided = (!wasOnGround && glided) || gliding;
				if (!wasOnGround && wasGliding && !gliding) {
					breaks++;
				}
			} else if (!wasOnGround) {
				glides.add(glided);
			}
			wasOnGround = onGround;
			wasGliding = gliding;
		}
		context.getInput().releaseKey(options -> options.keyJump);
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying(), FlightLab.LONG_TIMEOUT);

		LOGGER.info("Holding jump with Instant Fly{} for {} ticks: {} jumps, glided in each: {}; {} breaks in the air",
				instantLanding ? " and Instant Landing" : "", HOLD_JUMP_TICKS, glides.size(), glides, breaks);
		check(glides.size() >= 4, "holding jump should keep jumping, got " + glides.size() + " jumps");
		check(!glides.contains(false), "every jump should glide, got " + glides);
		check(breaks == 0, "the glides should not break off in the air, got " + breaks + " breaks");
	}

	/**
	 * Lets the server miss a landing: the client's packets from just before the landing until it jumped again reach
	 * the server at once, before the server's next tick looks at whether the player is on the ground. The server
	 * glides on while Instant Landing jumps without gliding, and pressing jump glides on with the server's glide
	 * instead of ending it.
	 */
	private static void checkMissedLanding(final ClientGameTestContext context, final FlightLab lab,
			final SimulatedLatency latency) {
		enableOnServer(context, Feature.INSTANT_LANDING);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		lab.server.runCommand("effect give @a minecraft:jump_boost infinite " + HIGH_JUMP_BOOST + " true");
		lab.launchGlide(0, 6, 0, 0.0F, 25.0F);
		lab.checkServerGliding(true, "the server should glide");
		context.waitFor(client -> client.player.getY() - lab.groundY < 1.5, FlightLab.LONG_TIMEOUT);
		latency.holdSending();
		context.getInput().holdKey(options -> options.keyJump);
		context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);
		context.waitFor(client -> !client.player.onGround(), FlightLab.SHORT_TIMEOUT);
		context.getInput().releaseKey(options -> options.keyJump);
		latency.sendHeld();

		check(lab.holdsFor(3 * ROUND_TRIP_TICKS, client -> !client.player.isFallFlying() && !client.player.onGround()),
				"Instant Landing should jump without gliding");
		check(lab.serverGliding(), "the server should have missed the landing and glided on");
		lab.pressJump();
		check(lab.gliding(), "pressing jump should glide on with the server's glide");
		check(lab.holdsFor(2 * ROUND_TRIP_TICKS, client -> client.player.isFallFlying()), "the glide should go on");
		lab.checkServerGliding(true, "pressing jump should not end the server's glide");
		context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying(), FlightLab.LONG_TIMEOUT);
		lab.checkServerGliding(false, "the server should end the glide on the next landing");
	}

	private static Sample sample(final FlightLab lab) {
		return lab.client(ElytraLatencyGameTest::sample);
	}

	private static Sample sample(final Minecraft client) {
		return new Sample(client.player.onGround(), client.player.isFallFlying(), client.player.getPose(),
				GlideController.isServerGliding(client.player), client.player.position());
	}

	private static String format(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/**
	 * The client's view of the local player: whether it is on the ground, whether it glides and in which pose, and
	 * whether the server's latest update says it glides.
	 */
	private record Sample(boolean onGround, boolean gliding, Pose pose, boolean serverGliding, Vec3 position) {
	}

	/**
	 * How a landing went: the ticks the client still glided on the ground, the ticks until the server's end of the
	 * glide arrived, and how far the player moved in that time.
	 */
	private record Landing(int glidingTicks, int serverEndTicks, double slide) {
	}
}
