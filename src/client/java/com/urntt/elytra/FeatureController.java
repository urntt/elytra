package com.urntt.elytra;

import com.urntt.elytra.config.ElytraConfig;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether each feature is active right now, from the configuration and the current {@link Scene}. It is the
 * only place that combines the toggle states, the contradicting feature groups, and the multiplayer rules.
 */
public final class FeatureController {
	/** Outcome of {@link #toggle(Feature)}. */
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

	public FeatureController(final ElytraConfig config) {
		this.config = config;
	}

	public Scene scene() {
		return this.scene;
	}

	public void onJoin(final Scene scene) {
		this.scene = scene;
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
	}

	/**
	 * Returns whether {@code feature} should act now: it is turned on and the multiplayer rules allow the mod here.
	 */
	public boolean isActive(final Feature feature) {
		return this.config.isEnabled(feature) && this.isAllowed();
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
	 * Flips and saves the toggle state of {@code feature}, unless the current server is not allowed.
	 */
	public ToggleResult toggle(final Feature feature) {
		if (this.scene instanceof Scene.Multiplayer && !this.isAllowed()) {
			return new ToggleResult(Outcome.BLOCKED, List.of());
		}
		boolean enabled = !this.config.isEnabled(feature);
		List<Feature> turnedOff = this.setEnabled(feature, enabled);
		return new ToggleResult(enabled ? Outcome.ENABLED : Outcome.DISABLED, turnedOff);
	}

	private boolean isListed(final @Nullable String address) {
		return address != null && this.config.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
