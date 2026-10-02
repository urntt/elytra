package com.urntt.elytra.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's settings, persisted as JSON. Every change is written to disk immediately.
 */
public final class ElytraConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(ElytraClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Settings settings;

	private ElytraConfig(final Path path, final Settings settings) {
		this.path = path;
		this.settings = settings;
	}

	/**
	 * Returns the location of the config file in the Fabric config directory.
	 */
	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(ElytraClient.MOD_ID + ".json");
	}

	/**
	 * Loads the configuration from {@code path}. A missing file is created with the defaults. A readable file is
	 * rewritten with any settings it lacks. An unreadable file falls back to the defaults and is left untouched
	 * until the next change is saved.
	 */
	public static ElytraConfig load(final Path path) {
		if (Files.notExists(path)) {
			Settings settings = new Settings();
			settings.normalize();
			ElytraConfig config = new ElytraConfig(path, settings);
			config.save();
			return config;
		}

		Settings settings;
		try {
			settings = GSON.fromJson(Files.readString(path), Settings.class);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to read config file {}, using defaults", path, e);
			Settings defaults = new Settings();
			defaults.normalize();
			return new ElytraConfig(path, defaults);
		}

		if (settings == null) {
			settings = new Settings();
		}
		settings.normalize();
		ElytraConfig config = new ElytraConfig(path, settings);
		config.save();
		return config;
	}

	/** Whether the player turned {@code feature} on. The multiplayer rules decide whether it is active. */
	public boolean isEnabled(final Feature feature) {
		return this.settings.features.get(feature.id());
	}

	public void setEnabled(final Feature feature, final boolean enabled) {
		this.settings.features.put(feature.id(), enabled);
		this.save();
	}

	public double get(final Tuning tuning) {
		return this.settings.tuning.get(tuning.id());
	}

	public void set(final Tuning tuning, final double value) {
		this.settings.tuning.put(tuning.id(), tuning.normalize(value));
		this.save();
	}

	/** Whether Chest Swap equips an elytra from the inventory when the player tries to glide without one. */
	public boolean chestSwapOnJump() {
		return this.settings.chestSwapOnJump;
	}

	public void setChestSwapOnJump(final boolean chestSwapOnJump) {
		this.settings.chestSwapOnJump = chestSwapOnJump;
		this.save();
	}

	/** Whether Chest Swap puts the chestplate back on after a glide it started ends. */
	public boolean chestSwapBack() {
		return this.settings.chestSwapBack;
	}

	public void setChestSwapBack(final boolean chestSwapBack) {
		this.settings.chestSwapBack = chestSwapBack;
		this.save();
	}

	public MultiplayerMode multiplayerMode() {
		return this.settings.multiplayerMode;
	}

	public void setMultiplayerMode(final MultiplayerMode multiplayerMode) {
		this.settings.multiplayerMode = multiplayerMode;
		this.save();
	}

	/** The server list used by the whitelist and blacklist modes. */
	public List<String> servers() {
		return List.copyOf(this.settings.servers);
	}

	public void setServers(final List<String> servers) {
		this.settings.servers = new ArrayList<>(servers);
		this.settings.normalize();
		this.save();
	}

	private void save() {
		try {
			Files.createDirectories(this.path.getParent());
			Files.writeString(this.path, GSON.toJson(this.settings));
		} catch (IOException e) {
			LOGGER.error("Failed to write config file {}", this.path, e);
		}
	}

	/**
	 * The serialized form. Defaults come from {@link Feature} and {@link Tuning} and from the field initializers, and
	 * also apply to keys missing from the file.
	 */
	private static final class Settings {
		private Map<String, Boolean> features = new LinkedHashMap<>();
		private Map<String, Double> tuning = new LinkedHashMap<>();
		private boolean chestSwapOnJump = true;
		private boolean chestSwapBack = true;
		private MultiplayerMode multiplayerMode = MultiplayerMode.DISABLED;
		private List<String> servers = new ArrayList<>();

		/**
		 * Fills in missing values, drops unknown keys, keeps only the first of several contradicting features turned
		 * on, rounds numbers to the values the settings accept, and tidies the server list.
		 */
		private void normalize() {
			Map<String, Boolean> features = new LinkedHashMap<>();
			for (Feature feature : Feature.values()) {
				Boolean enabled = this.features != null ? this.features.get(feature.id()) : null;
				boolean contradicted = Arrays.stream(Feature.values())
						.anyMatch(other -> other.conflictsWith(feature) && Boolean.TRUE.equals(features.get(other.id())));
				features.put(feature.id(), (enabled != null ? enabled : feature.enabledByDefault()) && !contradicted);
			}
			this.features = features;

			Map<String, Double> tuning = new LinkedHashMap<>();
			for (Tuning setting : Tuning.values()) {
				Double value = this.tuning != null ? this.tuning.get(setting.id()) : null;
				tuning.put(setting.id(), value != null ? setting.normalize(value) : setting.defaultValue());
			}
			this.tuning = tuning;

			if (this.multiplayerMode == null) {
				this.multiplayerMode = MultiplayerMode.DISABLED;
			}
			List<String> entries = new ArrayList<>();
			if (this.servers != null) {
				for (String entry : this.servers) {
					if (entry != null && !entry.isBlank()) {
						entries.add(entry.trim());
					}
				}
			}
			this.servers = entries;
		}
	}
}
