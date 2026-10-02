package com.urntt.elytra;

import com.urntt.elytra.config.ElytraConfig;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether each feature is active right now, from the configuration and the current {@link Scene}. It is the
 * only place that combines the main switch, its defaults and reset rules, the feature toggles, the contradicting
 * feature groups, and the multiplayer rules.
 */
public final class FeatureController {
	/** Outcome of {@link #toggle()} and {@link #toggle(Feature)}. */
	public enum Outcome {
		ENABLED,
		DISABLED,
		/** The current server is not allowed, so the toggle state was left unchanged. */
		BLOCKED
	}

	/**
	 * @param outcome what the toggle did
	 * @param turnedOff contradicting features that were turned off because the toggled feature was turned on
	 */
	public record ToggleResult(Outcome outcome, List<Feature> turnedOff) {
	}

	private final ElytraConfig config;
	private Scene scene = Scene.NONE;
	private boolean allowedWorldJoinedSinceStart = false;

	public FeatureController(final ElytraConfig config) {
		this.config = config;
	}

	public Scene scene() {
		return this.scene;
	}

	/**
	 * Called when the player joins a world. On an allowed scene, restores the main switch to the scene's default if a
	 * reset rule applies: always with "reset on world exit", and for the first allowed world since the game started
	 * with "reset on game exit". Applying the reset on join instead of on exit lets it pick the next scene's default
	 * and also works after a crash.
	 */
	public void onJoin(final Scene scene) {
		this.scene = scene;
		if (!this.isAllowed()) {
			return;
		}

		boolean firstWorld = !this.allowedWorldJoinedSinceStart;
		this.allowedWorldJoinedSinceStart = true;
		if (this.config.resetOnWorldExit() || (this.config.resetOnGameExit() && firstWorld)) {
			this.config.setEnabled(this.defaultFor(scene));
		}
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
	}

	/**
	 * Returns whether {@code feature} should act now: the main switch and the feature are on, and the multiplayer
	 * rules allow the mod here.
	 */
	public boolean isActive(final Feature feature) {
		return this.config.isEnabled() && this.config.isEnabled(feature) && this.isAllowed();
	}

	/**
	 * Returns whether the multiplayer rules allow the mod in the current scene.
	 */
	public boolean isAllowed() {
		return switch (this.scene) {
			case Scene.None none -> false;
			case Scene.Singleplayer singleplayer -> true;
			case Scene.Multiplayer multiplayer -> switch (this.config.multiplayerMode()) {
				case DISABLED -> false;
				case WHITELIST -> this.isListed(multiplayer.address());
				case BLACKLIST -> !this.isListed(multiplayer.address());
			};
		};
	}

	/**
	 * Turns {@code feature} on or off and saves it. Turning a feature on turns off the features it contradicts.
	 *
	 * @return the contradicting features that were turned off
	 */
	public List<Feature> setEnabled(final Feature feature, final boolean enabled) {
		List<Feature> turnedOff = new ArrayList<>();
		if (enabled) {
			for (Feature other : Feature.values()) {
				if (feature.conflictsWith(other) && this.config.isEnabled(other)) {
					this.config.setEnabled(other, false);
					turnedOff.add(other);
				}
			}
		}
		this.config.setEnabled(feature, enabled);
		return turnedOff;
	}

	/**
	 * Flips and saves the main switch, unless the current server is not allowed.
	 */
	public ToggleResult toggle() {
		if (this.isBlocked()) {
			return new ToggleResult(Outcome.BLOCKED, List.of());
		}
		boolean enabled = !this.config.isEnabled();
		this.config.setEnabled(enabled);
		return new ToggleResult(enabled ? Outcome.ENABLED : Outcome.DISABLED, List.of());
	}

	/**
	 * Flips and saves the toggle state of {@code feature}, unless the current server is not allowed.
	 */
	public ToggleResult toggle(final Feature feature) {
		if (this.isBlocked()) {
			return new ToggleResult(Outcome.BLOCKED, List.of());
		}
		boolean enabled = !this.config.isEnabled(feature);
		List<Feature> turnedOff = this.setEnabled(feature, enabled);
		return new ToggleResult(enabled ? Outcome.ENABLED : Outcome.DISABLED, turnedOff);
	}

	private boolean isBlocked() {
		return this.scene instanceof Scene.Multiplayer && !this.isAllowed();
	}

	private boolean defaultFor(final Scene scene) {
		return scene instanceof Scene.Singleplayer ? this.config.singleplayerDefault() : this.config.multiplayerDefault();
	}

	private boolean isListed(final @Nullable String address) {
		return address != null && this.config.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
