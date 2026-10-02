package com.urntt.elytra.gametest;

import static com.urntt.elytra.gametest.GameTestSupport.check;

import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import com.urntt.elytra.FeatureController;
import com.urntt.elytra.Scene;
import com.urntt.elytra.ServerAddresses;
import com.urntt.elytra.config.ElytraConfig;
import com.urntt.elytra.config.MultiplayerMode;
import com.urntt.elytra.config.Tuning;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Checks the defaults, the configuration file, the slider values, the contradicting features, the multiplayer
 * rules, and the address matching without a world. It runs first, so it also sees the configuration of a fresh
 * installation. The other checks use their own configuration files, so the game's configuration is left alone.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ElytraLogicGameTest implements FabricClientGameTest {
	private static final Scene SERVER_A = Scene.multiplayer("a.example.com");
	private static final Scene SERVER_B = Scene.multiplayer("b.example.com:25570");
	private static final Scene UNKNOWN_SERVER = Scene.multiplayer(null);

	@Override
	public void runTest(final ClientGameTestContext context) {
		// The run directory is deleted before each run, so this is the state of a fresh installation.
		checkDefaults(context.computeOnClient(client -> ElytraClient.config()));
		check(Files.exists(ElytraConfig.defaultPath()), "the config file should be created on start");

		Path directory = createTempDirectory();
		checkDefaults(ElytraConfig.load(directory.resolve("defaults.json")));
		checkNormalization(directory);
		checkTuningSteps();
		checkContradictingFeatures(directory);
		checkMultiplayerRules(directory);
		checkAddressMatching();
		GameTestSupport.LOGGER.info("Logic checks passed");
	}

	private static void checkDefaults(final ElytraConfig config) {
		for (Feature feature : Feature.values()) {
			boolean expected = feature == Feature.ELYTRA_BOOST || feature == Feature.INSTA_STOP;
			check(config.isEnabled(feature) == expected, feature + " should be " + (expected ? "on" : "off") + " by default");
		}
		check(config.get(Tuning.PARTIALLY_CONTROLLED_ASCEND_ACCELERATION) == 0.08, "jump should add 0.08 by default");
		check(config.get(Tuning.PARTIALLY_CONTROLLED_DESCEND_ACCELERATION) == 0.04, "sneak should take 0.04 by default");
		for (Tuning tuning : Tuning.values()) {
			check(config.get(tuning) == tuning.defaultValue(), tuning + " should start at its default");
		}
		check(config.chestSwapOnJump() && config.chestSwapBack(), "both Chest Swap options should be on by default");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "multiplayer should be disabled by default");
		check(config.servers().isEmpty(), "the server list should be empty by default");
	}

	private static void checkNormalization(final Path directory) {
		Path path = directory.resolve("normalize.json");
		write(path, """
				{
					"features": {"fake_elytra": true, "no_gliding": true, "autopilot": true, "fully_controlled": true,
						"insta_stop": false, "removed_feature": true},
					"tuning": {"fully_controlled.horizontal_speed": 99.0, "partially_controlled.ascend_acceleration": 0.083,
						"elytra_boost.duration": -5},
					"multiplayerMode": "bogus",
					"servers": [" a.example.com ", ""]
				}
				""");
		ElytraConfig config = ElytraConfig.load(path);
		check(config.isEnabled(Feature.FAKE_ELYTRA) && !config.isEnabled(Feature.NO_GLIDING),
				"of two contradicting features only the first should stay on");
		check(config.isEnabled(Feature.FULLY_CONTROLLED) && !config.isEnabled(Feature.AUTOPILOT),
				"of two flight controls only the first should stay on");
		check(!config.isEnabled(Feature.INSTA_STOP), "saved settings should be kept");
		check(config.isEnabled(Feature.ELYTRA_BOOST), "missing features should get their default");
		check(config.get(Tuning.FULLY_CONTROLLED_HORIZONTAL_SPEED) == 5.0, "values above the range should be clamped");
		check(config.get(Tuning.PARTIALLY_CONTROLLED_ASCEND_ACCELERATION) == 0.08, "values should be rounded to a step");
		check(config.get(Tuning.ELYTRA_BOOST_DURATION) == 1.0, "values below the range should be clamped");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "an unknown mode should fall back to disabled");
		check(config.servers().equals(List.of("a.example.com")), "entries should be trimmed and blanks dropped");
		String saved = read(path);
		check(!saved.contains("removed_feature"), "unknown features should be dropped from the file");
		check(saved.contains("\"chestSwapBack\"") && saved.contains("\"elytra_replace.min_durability\""),
				"missing settings should be written to the file");

		write(path, "{ not json");
		check(ElytraConfig.load(path).isEnabled(Feature.ELYTRA_BOOST), "an unreadable file should fall back to defaults");
		check(read(path).equals("{ not json"), "an unreadable file should be left untouched");
	}

	private static void checkTuningSteps() {
		for (Tuning tuning : Tuning.values()) {
			check(tuning.valueAt(tuning.stepOf(tuning.defaultValue())) == tuning.defaultValue(),
					tuning + "'s default should be a slider value");
			for (int step = 0; step <= tuning.steps(); step++) {
				check(tuning.stepOf(tuning.valueAt(step)) == step, tuning + " should map slider step " + step + " back");
			}
		}
	}

	private static void checkContradictingFeatures(final Path directory) {
		ElytraConfig config = ElytraConfig.load(directory.resolve("conflicts.json"));
		FeatureController controller = new FeatureController(config);
		controller.onJoin(Scene.SINGLEPLAYER);

		check(controller.setEnabled(Feature.AUTOPILOT, true).isEmpty(), "nothing should be turned off yet");
		check(controller.setEnabled(Feature.PARTIALLY_CONTROLLED, true).equals(List.of(Feature.AUTOPILOT)),
				"Partially Controlled Flying should turn Autopilot off");
		FeatureController.ToggleResult result = controller.toggle(Feature.FULLY_CONTROLLED);
		check(result.outcome() == FeatureController.Outcome.ENABLED
						&& result.turnedOff().equals(List.of(Feature.PARTIALLY_CONTROLLED)),
				"toggling Fully Controlled Flying on should turn Partially Controlled Flying off");
		check(controller.toggle(Feature.NO_GLIDING).turnedOff().isEmpty(), "No Gliding contradicts nothing that is on");
		check(controller.toggle(Feature.FAKE_ELYTRA).turnedOff().equals(List.of(Feature.NO_GLIDING)),
				"Fake Elytra should turn No Gliding off");
		check(controller.setEnabled(Feature.FULLY_CONTROLLED, false).isEmpty(), "turning off should not affect others");
		check(controller.isActive(Feature.FAKE_ELYTRA) && !controller.isActive(Feature.FULLY_CONTROLLED),
				"the states should match what was set");
	}

	private static void checkMultiplayerRules(final Path directory) {
		ElytraConfig config = ElytraConfig.load(directory.resolve("rules.json"));
		FeatureController controller = new FeatureController(config);
		Feature probe = Feature.ELYTRA_BOOST;

		check(!controller.isActive(probe), "nothing should be active outside a world");
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.isActive(probe), "singleplayer should always be allowed");

		controller.onJoin(SERVER_A);
		check(!controller.isAllowed() && !controller.isActive(probe), "the disabled mode should rule out every server");
		check(controller.toggle(probe).outcome() == FeatureController.Outcome.BLOCKED, "toggling should be blocked");
		check(config.isEnabled(probe), "a blocked toggle should leave the state unchanged");

		config.setServers(List.of("a.example.com"));
		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		check(controller.isActive(probe), "a whitelisted server should be allowed");
		controller.onJoin(SERVER_B);
		check(!controller.isAllowed(), "a server missing from the whitelist should be ruled out");
		controller.onJoin(UNKNOWN_SERVER);
		check(!controller.isAllowed(), "an unknown address should not count as whitelisted");

		config.setMultiplayerMode(MultiplayerMode.BLACKLIST);
		check(controller.isAllowed(), "an unknown address should not count as blacklisted");
		controller.onJoin(SERVER_B);
		check(controller.isAllowed(), "a server missing from the blacklist should be allowed");
		controller.onJoin(SERVER_A);
		check(!controller.isAllowed(), "a blacklisted server should be ruled out");

		controller.onDisconnect();
		check(!controller.isActive(probe), "nothing should be active after disconnecting");
	}

	private static void checkAddressMatching() {
		checkMatch("mc.example.com", "mc.example.com", true);
		checkMatch("MC.Example.com", "mc.example.COM:25565", true);
		checkMatch("mc.example.com", "mc.example.com:25566", true);
		checkMatch("mc.example.com:25566", "mc.example.com", false);
		checkMatch("mc.example.com", "play.example.com", false);
		checkMatch("192.168.1.5", "192.168.1.5:51234", true);
		checkMatch("[::1]:25565", "[::1]", true);
		checkMatch("bücher.example", "xn--bcher-kva.example", true);
		checkMatch("localhost", "localhost:41234", true);
		for (String invalid : List.of("", "   ", "mc.example.com:abc", "mc.example.com:99999", "mc example.com")) {
			check(!ServerAddresses.isValid(invalid), "'" + invalid + "' should be invalid");
		}
	}

	private static void checkMatch(final String entry, final String address, final boolean expected) {
		check(ServerAddresses.matches(entry, address) == expected,
				"'" + entry + "' should " + (expected ? "" : "not ") + "match '" + address + "'");
	}

	private static Path createTempDirectory() {
		try {
			return Files.createTempDirectory("elytra-gametest");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(final Path path, final String content) {
		try {
			Files.writeString(path, content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(final Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
