package com.urntt.elytra.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import com.urntt.elytra.config.ElytraConfig;
import com.urntt.elytra.config.MultiplayerMode;
import com.urntt.elytra.config.Tuning;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the client game tests.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("elytra-gametest");

	private GameTestSupport() {
	}

	/**
	 * Changes the mod's configuration on the client thread.
	 */
	static void configure(final ClientGameTestContext context, final Consumer<ElytraConfig> change) {
		context.runOnClient(client -> change.accept(ElytraClient.config()));
	}

	/**
	 * Turns the main switch and exactly the given features on, and restores every other setting to its default, so
	 * each check starts from a known configuration.
	 */
	static void onlyEnable(final ClientGameTestContext context, final Feature... enabled) {
		Set<Feature> features = Set.of(enabled);
		configure(context, config -> {
			config.setEnabled(true);
			config.setSingleplayerDefault(true);
			config.setMultiplayerDefault(true);
			config.setResetOnWorldExit(false);
			config.setResetOnGameExit(false);
			for (Feature feature : Feature.values()) {
				config.setEnabled(feature, features.contains(feature));
			}
			for (Tuning tuning : Tuning.values()) {
				config.set(tuning, tuning.defaultValue());
			}
			config.setChestSwapOnJump(true);
			config.setChestSwapBack(true);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
	}

	static boolean isEnabled(final ClientGameTestContext context, final Feature feature) {
		return context.computeOnClient(client -> ElytraClient.config().isEnabled(feature));
	}

	static ElytraConfig loadSavedConfig() {
		return ElytraConfig.load(ElytraConfig.defaultPath());
	}

	/**
	 * Returns the mod's key mapping registered under {@code name}, bound to {@code key} for the test.
	 */
	static KeyMapping bindKey(final ClientGameTestContext context, final String name, final String key) {
		KeyMapping mapping = Objects.requireNonNull(KeyMapping.get(name), name);
		context.runOnClient(client -> {
			mapping.setKey(InputConstants.getKey(key));
			KeyMapping.resetMapping();
		});
		return mapping;
	}

	static void unbindKey(final ClientGameTestContext context, final KeyMapping mapping) {
		context.runOnClient(client -> {
			mapping.setKey(mapping.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
