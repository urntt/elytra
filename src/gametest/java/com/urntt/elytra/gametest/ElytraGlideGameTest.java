package com.urntt.elytra.gametest;

import static com.urntt.elytra.gametest.GameTestSupport.LOGGER;
import static com.urntt.elytra.gametest.GameTestSupport.bindKey;
import static com.urntt.elytra.gametest.GameTestSupport.check;
import static com.urntt.elytra.gametest.GameTestSupport.configure;
import static com.urntt.elytra.gametest.GameTestSupport.isEnabled;
import static com.urntt.elytra.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.elytra.gametest.GameTestSupport.onlyEnable;
import static com.urntt.elytra.gametest.GameTestSupport.unbindKey;

import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import com.urntt.elytra.config.ElytraConfigScreen;
import com.urntt.elytra.config.MultiplayerMode;
import com.urntt.elytra.config.ServerListScreen;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

/**
 * Checks the features that start and stop glides, Keep Pose While Gliding, the toggle keys, the main switch and its
 * reset on world exit, and the settings screens in singleplayer. Each check measures the vanilla behavior with the feature off and the changed
 * behavior with it on, in the same setup.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ElytraGlideGameTest implements FabricClientGameTest {
	private static final String ELYTRA = "minecraft:elytra";
	/** How close, in blocks, the camera must stay to the standing eye height. A dip toward gliding is about 0.6. */
	private static final float CAMERA_TOLERANCE = 0.01F;
	/** How long the jump key is held in the repeated Instant Fly check. */
	private static final int HOLD_JUMP_TICKS = 300;
	/** Screenshots needed to show the whole settings list, and the mouse wheel steps between two of them. */
	private static final int SETTINGS_PAGES = 8;
	private static final int SCROLL_PER_PAGE = 9;

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			FlightLab lab = new FlightLab(context, singleplayer.getServer());

			checkToggleKeys(context, lab);
			checkInstantFly(context, lab);
			checkInstantFlyHoldingJump(context, lab);
			checkMainSwitch(context, lab);
			checkInstantStop(context, lab);
			checkNoGliding(context, lab);
			checkFakeElytra(context, lab);
			checkGroundGlide(context, lab);
			checkStopInWater(context, lab);
			checkKeepPose(context, lab);
			checkOpenSettingsKey(context);

			// Leave this world with the main switch off to check the reset on world exit below.
			configure(context, config -> {
				config.setResetOnWorldExit(true);
				config.setEnabled(false);
			});
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(context.computeOnClient(client -> ElytraClient.config().isEnabled()),
					"reset on world exit should restore the main switch's singleplayer default");
		}
		// Outside a world: closing a singleplayer world right after a pause screen can hang the test harness.
		checkSettingsScreens(context);
		onlyEnable(context);
	}

	private static void checkToggleKeys(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context);
		lab.resetPlayer(0, 0);
		KeyMapping fullKey = bindKey(context, Feature.FULLY_CONTROLLED.toggleKeyName(), "key.keyboard.j");
		KeyMapping partialKey = bindKey(context, Feature.PARTIALLY_CONTROLLED.toggleKeyName(), "key.keyboard.k");

		context.getInput().pressKey(fullKey);
		context.waitTick();
		check(isEnabled(context, Feature.FULLY_CONTROLLED), "the toggle key should turn Fully Controlled Flying on");
		check(loadSavedConfig().isEnabled(Feature.FULLY_CONTROLLED), "the toggled state should be saved");

		context.getInput().pressKey(partialKey);
		context.waitTick();
		check(isEnabled(context, Feature.PARTIALLY_CONTROLLED), "the toggle key should turn Partially Controlled Flying on");
		check(!isEnabled(context, Feature.FULLY_CONTROLLED), "turning one flight control on should turn the other off");
		check(!loadSavedConfig().isEnabled(Feature.FULLY_CONTROLLED), "the turned-off feature should be saved");
		context.takeScreenshot("elytra-toggle-with-conflict");

		context.getInput().pressKey(partialKey);
		context.waitTick();
		check(!isEnabled(context, Feature.PARTIALLY_CONTROLLED), "the toggle key should turn the feature off again");
		unbindKey(context, fullKey);
		unbindKey(context, partialKey);
	}

	/**
	 * A single press of jump on the ground only jumps in vanilla; with Instant Fly the glide starts right after.
	 */
	private static void checkInstantFly(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		lab.pressJump();
		check(lab.holdsFor(15, client -> !client.player.isFallFlying()), "a jump without Instant Fly should not glide");
		context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);

		onlyEnable(context, Feature.INSTANT_FLY);
		context.waitTick();
		lab.pressJump();
		int ticks = lab.ticksUntil(5, client -> client.player.isFallFlying());
		LOGGER.info("Instant Fly started gliding {} ticks after the jump key, {} blocks above the ground", ticks, lab.height());
		check(ticks >= 0, "Instant Fly should start gliding right after the jump");
		lab.checkServerGliding(true, "the server should accept the glide Instant Fly started");
	}

	/**
	 * With the main switch off, a feature that is turned on does nothing; the main switch's key turns it back on.
	 */
	private static void checkMainSwitch(final ClientGameTestContext context, final FlightLab lab) {
		KeyMapping mainKey = bindKey(context, ElytraClient.TOGGLE_KEY_NAME, "key.keyboard.j");
		onlyEnable(context, Feature.INSTANT_FLY);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);

		context.getInput().pressKey(mainKey);
		context.waitTick();
		check(!context.computeOnClient(client -> ElytraClient.config().isEnabled()), "the key should turn the main switch off");
		check(isEnabled(context, Feature.INSTANT_FLY), "the main switch should leave Instant Fly turned on");
		check(!loadSavedConfig().isEnabled(), "the main switch's state should be saved");
		context.takeScreenshot("elytra-main-switch-off");
		lab.pressJump();
		check(lab.holdsFor(15, client -> !client.player.isFallFlying()), "Instant Fly should not act with the main switch off");
		context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);

		context.getInput().pressKey(mainKey);
		context.waitTick();
		lab.pressJump();
		check(lab.ticksUntil(5, client -> client.player.isFallFlying()) >= 0,
				"Instant Fly should act again once the main switch is back on");
		unbindKey(context, mainKey);
	}

	/**
	 * Holding jump and forward with Instant Fly (and Ground Glide off) should glide from every jump, including the jumps
	 * made right on landing from a glide, before the server's end of that glide has reached the client.
	 */
	private static void checkInstantFlyHoldingJump(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context, Feature.INSTANT_FLY);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		context.getInput().holdKey(options -> options.keyJump);
		context.getInput().holdKey(options -> options.keyUp);

		// For each time in the air, whether the player glided at some point.
		List<Boolean> glides = new ArrayList<>();
		boolean wasOnGround = true;
		boolean glided = false;
		for (int tick = 0; tick < HOLD_JUMP_TICKS; tick++) {
			context.waitTick();
			boolean onGround = lab.client(client -> client.player.onGround());
			if (!onGround) {
				glided = (!wasOnGround && glided) || lab.client(client -> client.player.isFallFlying());
			} else if (!wasOnGround) {
				glides.add(glided);
			}
			wasOnGround = onGround;
		}
		context.getInput().releaseKey(options -> options.keyJump);
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying(), FlightLab.LONG_TIMEOUT);

		LOGGER.info("Holding jump with Instant Fly for {} ticks: {} jumps, glided in each: {}", HOLD_JUMP_TICKS,
				glides.size(), glides);
		check(glides.size() >= 4, "holding jump should keep jumping, got " + glides.size() + " jumps");
		check(!glides.contains(false), "every jump should glide, got " + glides);
	}

	private static void checkInstantStop(final ClientGameTestContext context, final FlightLab lab) {
		KeyMapping stopKey = bindKey(context, ElytraClient.INSTANT_STOP_KEY_NAME, "key.keyboard.j");
		onlyEnable(context);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		lab.launchGlide(0, 60, 0, 0.0F, 0.0F);
		context.getInput().pressKey(stopKey);
		check(lab.holdsFor(5, client -> client.player.isFallFlying()), "the stop key should do nothing with Instant Stop off");
		context.takeScreenshot("elytra-instant-stop-off");

		onlyEnable(context, Feature.INSTANT_STOP);
		context.getInput().pressKey(stopKey);
		int ticks = lab.ticksUntil(2, client -> !client.player.isFallFlying());
		LOGGER.info("Instant Stop ended the glide after {} ticks", ticks);
		check(ticks >= 0, "Instant Stop should end the glide at once");
		lab.checkServerGliding(false, "the server should end the glide too");
		check(lab.holdsFor(10, client -> !client.player.isFallFlying()), "the glide should stay ended");
		unbindKey(context, stopKey);
	}

	private static void checkNoGliding(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context, Feature.NO_GLIDING);
		lab.resetPlayer(0, 0);
		lab.wear(ELYTRA);
		lab.teleport(0, lab.groundY + 60, 0, 0.0F, 0.0F);
		context.waitTicks(2);
		lab.pressJump();
		check(lab.holdsFor(10, client -> !client.player.isFallFlying()), "No Gliding should prevent gliding");
		check(!lab.serverGliding(), "the server should not glide either");

		onlyEnable(context);
		lab.launchGlide(0, 60, 0, 0.0F, 0.0F);
		check(lab.gliding(), "the same setup should glide with No Gliding off");
	}

	/**
	 * Without an elytra, vanilla free-falls; Fake Elytra glides on the client only. The server does not glide, so it
	 * counts the descent as a fall, which the README documents.
	 */
	private static void checkFakeElytra(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context);
		lab.resetPlayer(0, 0);
		lab.teleport(0, lab.groundY + 60, 0, 0.0F, 0.0F);
		context.waitTicks(2);
		lab.pressJump();
		check(lab.holdsFor(10, client -> !client.player.isFallFlying()), "without an elytra vanilla should not glide");
		double fallingSpeed = lab.velocity().y;

		onlyEnable(context, Feature.FAKE_ELYTRA);
		lab.launchGlide(0, 60, 0, 0.0F, 0.0F);
		check(lab.holdsFor(20, client -> client.player.isFallFlying()), "Fake Elytra should keep gliding");
		double glidingSpeed = lab.velocity().y;
		LOGGER.info("Vertical speed without an elytra: falling {}, Fake Elytra gliding {}", fallingSpeed, glidingSpeed);
		check(glidingSpeed > -0.5 && fallingSpeed < -0.5, "the fake glide should descend like a glide, not a fall");
		check(!lab.serverGliding(), "the server should not know about the fake glide");
		context.takeScreenshot("elytra-fake-elytra");

		// Land from a low glide without damage protection to measure how the server counts the descent.
		lab.resetPlayer(0, 0);
		lab.server.runCommand("effect clear @a minecraft:resistance");
		lab.heal();
		context.waitTicks(2);
		float healthBefore = lab.client(client -> client.player.getHealth());
		lab.launchGlide(0, 12, 0, 0.0F, 0.0F);
		context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);
		context.waitTicks(5);
		float healthAfter = lab.client(client -> client.player.getHealth());
		LOGGER.info("Health after a fake glide from 12 blocks: {} -> {}", healthBefore, healthAfter);
		check(healthAfter < healthBefore, "the server should count a fake glide's descent as a fall");
		lab.heal();
	}

	/**
	 * In vanilla a glide ends when the player touches the ground; with Ground Glide it keeps sliding.
	 */
	private static void checkGroundGlide(final ClientGameTestContext context, final FlightLab lab) {
		for (boolean enabled : new boolean[] {false, true}) {
			if (enabled) {
				onlyEnable(context, Feature.GROUND_GLIDE);
			} else {
				onlyEnable(context);
			}
			lab.resetPlayer(0, 0);
			lab.wear(ELYTRA);
			lab.launchGlide(0, 8, 0, 0.0F, 0.0F);
			context.waitFor(client -> client.player.onGround(), FlightLab.LONG_TIMEOUT);
			context.waitTicks(20);
			boolean gliding = lab.gliding();
			double speed = lab.velocity().horizontalDistance();
			LOGGER.info("20 ticks after landing with Ground Glide {}: gliding {}, horizontal speed {}",
					enabled ? "on" : "off", gliding, speed);
			check(gliding == enabled, enabled ? "Ground Glide should keep gliding on the ground"
					: "vanilla should end the glide on the ground");
			if (enabled) {
				check(lab.client(FlightLab::isClientGliding), "the glide on the ground should be client-side");
				context.takeScreenshot("elytra-ground-glide");
			}
		}
	}

	/**
	 * In vanilla a glide continues in water; with Stop Flying in Water it ends on entering.
	 */
	private static void checkStopInWater(final ClientGameTestContext context, final FlightLab lab) {
		int y = (int) Math.floor(lab.groundY);
		// Replace the grass and dirt (not the bedrock) with a pool.
		lab.server.runCommand("fill -12 %d 20 12 %d 60 minecraft:water".formatted(y - 3, y - 1));
		for (boolean enabled : new boolean[] {false, true}) {
			if (enabled) {
				onlyEnable(context, Feature.STOP_IN_WATER);
			} else {
				onlyEnable(context);
			}
			lab.resetPlayer(0, 0);
			lab.wear(ELYTRA);
			lab.launchGlide(0, 6, 30, 0.0F, 30.0F);
			context.waitFor(client -> client.player.isInWater(), FlightLab.LONG_TIMEOUT);
			context.waitTicks(3);
			boolean gliding = lab.gliding();
			LOGGER.info("3 ticks after entering water with Stop Flying in Water {}: gliding {}", enabled ? "on" : "off", gliding);
			check(gliding != enabled, enabled ? "Stop Flying in Water should end the glide"
					: "vanilla should keep gliding in water");
			if (enabled) {
				lab.checkServerGliding(false, "the server should end the glide in water too");
			}
		}
	}

	/**
	 * A vanilla glide lies down: a low hitbox, low eyes, the gliding animation, and spread wings. Keep Pose While
	 * Gliding keeps the standing pose, hitbox, eye height, and animation, while the glide itself goes on.
	 */
	private static void checkKeepPose(final ClientGameTestContext context, final FlightLab lab) {
		for (boolean enabled : new boolean[] {false, true}) {
			if (enabled) {
				onlyEnable(context, Feature.KEEP_POSE);
			} else {
				onlyEnable(context);
			}
			lab.resetPlayer(0, 0);
			lab.wear(ELYTRA);
			// The camera follows the eye height smoothly; let it settle after the previous glide.
			float standingEyeHeight = lab.client(client -> client.player.getDimensions(Pose.STANDING).eyeHeight());
			context.waitFor(client -> Math.abs(cameraEyeHeight(client) - standingEyeHeight) < CAMERA_TOLERANCE,
					FlightLab.LONG_TIMEOUT);
			lab.launchGlide(0, 60, 0, 0.0F, 0.0F);
			// Long enough for the server's pose update to arrive and for the wings to settle.
			int lyingTicks = 0;
			float lowestCamera = Float.MAX_VALUE;
			for (int tick = 0; tick < 20; tick++) {
				context.waitTick();
				if (lab.client(client -> client.player.getPose() == Pose.FALL_FLYING)) {
					lyingTicks++;
				}
				lowestCamera = Math.min(lowestCamera, lab.client(ElytraGlideGameTest::cameraEyeHeight));
			}
			PoseSample sample = lab.client(client -> {
				EntityRenderer<? super LocalPlayer, ?> renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
				HumanoidRenderState state = (HumanoidRenderState) renderer.createRenderState(client.player, 1.0F);
				return new PoseSample(client.player.isFallFlying(), client.player.getPose(),
						client.player.getBbHeight(), client.player.getEyeHeight(), state.isFallFlying, state.elytraRotZ);
			});
			EntityDimensions standing = lab.client(client -> client.player.getDimensions(Pose.STANDING));
			EntityDimensions lying = lab.client(client -> client.player.getDimensions(Pose.FALL_FLYING));
			LOGGER.info("Gliding with Keep Pose While Gliding {}: {}, lying down in {} of 20 ticks, lowest camera height {}",
					enabled ? "on" : "off", sample, lyingTicks, lowestCamera);

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(2);
			context.takeScreenshot("elytra-keep-pose-" + (enabled ? "on" : "off"));
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			check(sample.gliding(), "the player should glide either way");
			if (enabled) {
				check(lyingTicks == 0 && sample.pose() == Pose.STANDING, "the player should keep standing while gliding");
				check(sample.height() == standing.height() && sample.eyeHeight() == standing.eyeHeight(),
						"the hitbox and the eyes should stay at standing height");
				check(lowestCamera > standing.eyeHeight() - CAMERA_TOLERANCE,
						"the camera should never dip toward the gliding height");
				// The folded elytra rests at -PI/12; gliding spreads it toward -PI/2.
				check(!sample.renderedGliding() && sample.wingRotZ() > -Math.PI / 6,
						"the body and the elytra should be drawn as when not gliding");
			} else {
				check(sample.pose() == Pose.FALL_FLYING && sample.height() == lying.height()
								&& sample.eyeHeight() == lying.eyeHeight(),
						"a vanilla glide should lie down with a low hitbox and low eyes");
				check(sample.renderedGliding() && sample.wingRotZ() < -Math.PI / 4,
						"a vanilla glide should be drawn gliding with spread wings");
			}
		}
	}

	/**
	 * Returns the camera's height above the player's feet, which follows the eye height with some smoothing.
	 */
	private static float cameraEyeHeight(final Minecraft client) {
		try {
			Field eyeHeight = Camera.class.getDeclaredField("eyeHeight");
			eyeHeight.setAccessible(true);
			return eyeHeight.getFloat(client.gameRenderer.mainCamera());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot read the camera's eye height", e);
		}
	}

	private record PoseSample(boolean gliding, Pose pose, float height, float eyeHeight, boolean renderedGliding,
			float wingRotZ) {
	}

	private static void checkOpenSettingsKey(final ClientGameTestContext context) {
		KeyMapping openSettingsKey = bindKey(context, ElytraClient.OPEN_SETTINGS_KEY_NAME, "key.keyboard.k");
		context.getInput().pressKey(openSettingsKey);
		context.waitForScreen(ElytraConfigScreen.class);
		context.setScreen(() -> null);
		// Let the game unpause before anything else happens in the world.
		context.waitTicks(5);
		unbindKey(context, openSettingsKey);
	}

	/**
	 * Takes screenshots of both settings screens in English and in Simplified Chinese.
	 */
	private static void checkSettingsScreens(final ClientGameTestContext context) {
		onlyEnable(context, Feature.PARTIALLY_CONTROLLED, Feature.ELYTRA_BOOST, Feature.INSTANT_STOP);
		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			config.setServers(List.of("mc.example.com", "192.168.1.5"));
		});
		takeSettingsScreenshots(context, "en_us");
		switchLanguage(context, "zh_cn");
		takeSettingsScreenshots(context, "zh_cn");
		switchLanguage(context, "en_us");
		onlyEnable(context);
	}

	private static void takeSettingsScreenshots(final ClientGameTestContext context, final String language) {
		// Recipe toasts would cover the top right corner.
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.setScreen(() -> new ElytraConfigScreen(null));
		// Opening a screen centers the cursor. Move it to the left edge: still over the list, so the mouse wheel scrolls
		// it, but beside the options, so no tooltip covers them.
		double middle = context.computeOnClient(client -> client.getWindow().getScreenHeight() / 2.0);
		context.getInput().setCursorPos(4, middle);
		context.waitTick();
		context.takeScreenshot("elytra-config-screen-1-" + language);
		for (int page = 2; page <= SETTINGS_PAGES; page++) {
			context.getInput().scroll(-SCROLL_PER_PAGE);
			context.waitTick();
			context.takeScreenshot("elytra-config-screen-" + page + "-" + language);
		}

		context.setScreen(() -> new ServerListScreen(new ElytraConfigScreen(null), ElytraClient.config()));
		context.takeScreenshot("elytra-server-list-" + language);
		context.setScreen(() -> null);
	}

	private static void switchLanguage(final ClientGameTestContext context, final String language) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(language);
			client.options.languageCode = language;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
	}
}
