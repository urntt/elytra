package com.urntt.elytra;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The features the player can turn on and off individually, each with its own toggle key.
 *
 * <p>Features that act on their own are off by default. Features that only act when their own key is pressed (and
 * those keys are unbound by default) are on by default.
 */
public enum Feature {
	FAKE_ELYTRA("fake_elytra", false, Group.GLIDE_PERMISSION),
	NO_GLIDING("no_gliding", false, Group.GLIDE_PERMISSION),
	GROUND_GLIDE("ground_glide", false, null),
	INSTANT_FLY("instant_fly", false, null),
	STOP_IN_WATER("stop_in_water", false, null),
	FULLY_CONTROLLED("fully_controlled", false, Group.FLIGHT_CONTROL),
	PARTIALLY_CONTROLLED("partially_controlled", false, Group.FLIGHT_CONTROL),
	NO_CRASH("no_crash", false, null),
	AUTOPILOT("autopilot", false, Group.FLIGHT_CONTROL),
	ELYTRA_REPLACE("elytra_replace", false, null),
	CHEST_SWAP("chest_swap", false, null),
	ELYTRA_BOOST("elytra_boost", true, null),
	INSTA_STOP("insta_stop", true, null);

	/**
	 * Features that contradict each other. Turning one on turns the others in its group off.
	 */
	public enum Group {
		/** Gliding without an elytra versus never gliding. */
		GLIDE_PERMISSION,
		/** Ways of steering a glide that replace or override each other. */
		FLIGHT_CONTROL
	}

	private final String id;
	private final boolean enabledByDefault;
	private final Optional<Group> group;

	Feature(final String id, final boolean enabledByDefault, final Group group) {
		this.id = id;
		this.enabledByDefault = enabledByDefault;
		this.group = Optional.ofNullable(group);
	}

	/** The name used in the configuration file and in translation keys. */
	public String id() {
		return this.id;
	}

	public boolean enabledByDefault() {
		return this.enabledByDefault;
	}

	public Optional<Group> group() {
		return this.group;
	}

	/** Returns whether turning this feature on turns {@code other} off. */
	public boolean conflictsWith(final Feature other) {
		return other != this && this.group.isPresent() && this.group.equals(other.group);
	}

	/** Translation key of the feature's name, used on the configuration screen and in action bar messages. */
	public String nameKey() {
		return "options.elytra.feature." + this.id;
	}

	public Component displayName() {
		return Component.translatable(this.nameKey());
	}

	/** Translation key of the key binding that toggles the feature. */
	public String toggleKeyName() {
		return "key.elytra.toggle." + this.id;
	}
}
