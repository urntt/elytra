package com.urntt.elytra.config;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Decides on which multiplayer servers the mod may be active. On servers it rules out, every feature stays off
 * regardless of which features are turned on.
 */
public enum MultiplayerMode implements StringRepresentable {
	/** The mod is never active in multiplayer. */
	@SerializedName("disabled")
	DISABLED("disabled"),
	/** The mod may be active only on servers in the server list. */
	@SerializedName("whitelist")
	WHITELIST("whitelist"),
	/** The mod may be active on every server except those in the server list. */
	@SerializedName("blacklist")
	BLACKLIST("blacklist");

	public static final Codec<MultiplayerMode> CODEC = StringRepresentable.fromEnum(MultiplayerMode::values);

	private final String name;

	MultiplayerMode(final String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public Component label() {
		return Component.translatable("options.elytra.multiplayer_mode." + this.name);
	}

	public Component description() {
		return Component.translatable("options.elytra.multiplayer_mode." + this.name + ".description");
	}
}
