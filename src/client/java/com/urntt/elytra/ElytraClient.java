package com.urntt.elytra;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.elytra.config.ElytraConfig;
import com.urntt.elytra.config.ElytraConfigScreen;
import com.urntt.elytra.flight.CrashGuard;
import com.urntt.elytra.flight.ElytraBoost;
import com.urntt.elytra.flight.FlightControl;
import com.urntt.elytra.flight.GlideController;
import com.urntt.elytra.inventory.ElytraEquipment;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;

public final class ElytraClient implements ClientModInitializer {
	public static final String MOD_ID = "elytra";
	public static final String BOOST_KEY_NAME = "key.elytra.boost";
	public static final String INSTA_STOP_KEY_NAME = "key.elytra.insta_stop";
	public static final String SWAP_KEY_NAME = "key.elytra.swap_chest";
	public static final String OPEN_SETTINGS_KEY_NAME = "key.elytra.open_settings";

	/** Action bar message shown when a key is pressed on a server the multiplayer rules rule out. */
	public static final Component BLOCKED_MESSAGE = Component.translatable("message.elytra.blocked");

	private static ElytraConfig config;
	private static FeatureController features;
	private static GlideController glide;
	private static FlightControl flight;
	private static CrashGuard crashGuard;
	private static ElytraBoost boost;
	private static ElytraEquipment equipment;

	@Override
	public void onInitializeClient() {
		config = ElytraConfig.load(ElytraConfig.defaultPath());
		features = new FeatureController(config);
		equipment = new ElytraEquipment(features, config);
		glide = new GlideController(features, equipment);
		flight = new FlightControl(features, config);
		crashGuard = new CrashGuard(features);
		boost = new ElytraBoost(config);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		Map<Feature, KeyMapping> toggleKeys = new EnumMap<>(Feature.class);
		for (Feature feature : Feature.values()) {
			toggleKeys.put(feature, register(feature.toggleKeyName(), category));
		}
		KeyMapping boostKey = register(BOOST_KEY_NAME, category);
		KeyMapping instaStopKey = register(INSTA_STOP_KEY_NAME, category);
		KeyMapping swapKey = register(SWAP_KEY_NAME, category);
		KeyMapping openSettingsKey = register(OPEN_SETTINGS_KEY_NAME, category);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// While singleplayer is paused, the player and the rockets do not move, and the server would only process
			// queued commands after resuming.
			LocalPlayer player = client.player;
			boolean running = player != null && !client.isPaused();
			if (running) {
				// Count down rockets launched in earlier ticks before launching new ones.
				boost.tick();
			}

			toggleKeys.forEach((feature, key) -> {
				while (key.consumeClick()) {
					toggle(client, feature);
				}
			});
			while (boostKey.consumeClick()) {
				if (checkActive(client, Feature.ELYTRA_BOOST)) {
					boost.launch(client.player);
				}
			}
			while (instaStopKey.consumeClick()) {
				if (checkActive(client, Feature.INSTA_STOP) && client.player.isFallFlying()) {
					glide.stopGliding(client.player);
				}
			}
			while (swapKey.consumeClick()) {
				if (checkActive(client, Feature.CHEST_SWAP)) {
					Component message = equipment.swapManually(client.player).message();
					if (message != null) {
						client.player.sendOverlayMessage(message);
					}
				}
			}
			while (openSettingsKey.consumeClick()) {
				client.gui.setScreen(new ElytraConfigScreen(client.gui.screen()));
			}

			if (running) {
				glide.tick(player);
				equipment.tick(player);
			}
		});

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> features.onJoin(Scene.of(client)));
		// The disconnect event may arrive on the network thread; the controllers are only used on the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(() -> {
			features.onDisconnect();
			boost.clear();
		}));
	}

	public static ElytraConfig config() {
		return config;
	}

	public static FeatureController features() {
		return features;
	}

	public static GlideController glide() {
		return glide;
	}

	public static FlightControl flight() {
		return flight;
	}

	public static CrashGuard crashGuard() {
		return crashGuard;
	}

	public static ElytraBoost boost() {
		return boost;
	}

	private static KeyMapping register(final String name, final KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.UNKNOWN.getValue(), category));
	}

	private static void toggle(final Minecraft client, final Feature feature) {
		FeatureController.ToggleResult result = features.toggle(feature);
		if (client.player == null) {
			return;
		}

		Component message = switch (result.outcome()) {
			case ENABLED -> CommonComponents.optionStatus(feature.displayName(), true);
			case DISABLED -> CommonComponents.optionStatus(feature.displayName(), false);
			case BLOCKED -> BLOCKED_MESSAGE;
		};
		if (!result.turnedOff().isEmpty()) {
			Component turnedOff = ComponentUtils.formatList(result.turnedOff(), Feature::displayName);
			message = Component.translatable("message.elytra.turned_off", message, turnedOff);
		}
		client.player.sendOverlayMessage(message);
	}

	/**
	 * Returns whether a key of {@code feature} may act now, and otherwise tells the player why not.
	 */
	private static boolean checkActive(final Minecraft client, final Feature feature) {
		if (client.player == null) {
			return false;
		}
		if (features.isActive(feature)) {
			return true;
		}
		client.player.sendOverlayMessage(features.isAllowed()
				? Component.translatable("message.elytra.feature_off", feature.displayName())
				: BLOCKED_MESSAGE);
		return false;
	}
}
