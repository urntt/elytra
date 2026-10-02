package com.urntt.elytra;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The features the player can turn on and off individually, each with its own toggle key. All features are off by
 * default, and the mod's main switch ({@code ElytraConfig.isEnabled()}) gates all of them at once.
 */
public enum Feature {
	FAKE_ELYTRA("fake_elytra", Group.GLIDE_PERMISSION),
	NO_GLIDING("no_gliding", Group.GLIDE_PERMISSION),
	GROUND_GLIDE("ground_glide", null),
	INSTANT_FLY("instant_fly", null),
	STOP_IN_WATER("stop_in_water", null),
	FULLY_CONTROLLED("fully_controlled", Group.FLIGHT_CONTROL),
	PARTIALLY_CONTROLLED("partially_controlled", Group.FLIGHT_CONTROL),
	NO_CRASH("no_crash", null),
	AUTOPILOT("autopilot", Group.FLIGHT_CONTROL),
	ELYTRA_REPLACE("elytra_replace", null),
	CHEST_SWAP("chest_swap", null),
	ELYTRA_BOOST("elytra_boost", null),
	INSTA_STOP("insta_stop", null);

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
	private final Optional<Group> group;

	Feature(final String id, final Group group) {
		this.id = id;
		this.group = Optional.ofNullable(group);
	}

	/** The name used in the configuration file and in translation keys. */
	public String id() {
		return this.id;
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
