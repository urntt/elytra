package com.urntt.elytra.gametest;

import static com.urntt.elytra.gametest.GameTestSupport.check;

import com.urntt.elytra.ElytraClient;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the local player in a test world: equipment, teleports, starting glides, and reading the player's state on
 * the client and on the server.
 */
@SuppressWarnings("UnstableApiUsage")
final class FlightLab {
	/** Ticks to wait for something that should happen almost at once. */
	static final int SHORT_TIMEOUT = 20;
	/** Ticks to wait for a landing or another slow change. */
	static final int LONG_TIMEOUT = 400;

	final ClientGameTestContext context;
	final TestServerContext server;
	/** The height of the superflat ground the player stands on. */
	final double groundY;

	FlightLab(final ClientGameTestContext context, final TestServerContext server) {
		this.context = context;
		this.server = server;
		server.runCommand("gamemode survival @a");
		server.runCommand("time set day");
		context.waitFor(client -> client.player != null && client.player.onGround(), LONG_TIMEOUT);
		this.groundY = this.client(client -> client.player.getY());
	}

	<T> T client(final Function<Minecraft, T> function) {
		return this.context.computeOnClient(function::apply);
	}

	/**
	 * Clears the inventory and effects, makes the player immune to damage, and puts it on the ground at
	 * {@code (x, z)}, no longer gliding.
	 */
	void resetPlayer(final double x, final double z) {
		this.server.runCommand("clear @a");
		this.server.runCommand("effect clear @a");
		this.server.runCommand("effect give @a minecraft:resistance infinite 4 true");
		this.server.runCommand("effect give @a minecraft:saturation infinite 0 true");
		this.teleport(x, this.groundY, z, 0.0F, 0.0F);
		this.context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying(), LONG_TIMEOUT);
		this.server.waitFor(server -> !serverPlayer(server).isFallFlying(), LONG_TIMEOUT);
		this.context.waitTick();
	}

	void heal() {
		this.server.runCommand("effect give @a minecraft:instant_health 1 10 true");
	}

	void wear(final String item) {
		this.server.runCommand("item replace entity @a armor.chest with " + item);
		this.context.waitFor(client -> !client.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), SHORT_TIMEOUT);
	}

	void give(final String item) {
		this.server.runCommand("give @a " + item);
		this.context.waitTicks(2);
	}

	/**
	 * Teleports the player and waits until the client is there. Yaw 0 faces south (+Z); positive pitch looks down.
	 */
	void teleport(final double x, final double y, final double z, final float yaw, final float pitch) {
		this.server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f", x, y, z, yaw, pitch));
		this.context.waitFor(client -> client.player.position().distanceToSqr(x, y, z) < 0.25, SHORT_TIMEOUT);
	}

	/** Holds the jump key for exactly one tick. */
	void pressJump() {
		this.context.getInput().holdKeyFor(options -> options.keyJump, 1);
	}

	/**
	 * Teleports the player into the air at {@code height} blocks above the ground and starts a glide with the jump
	 * key, as a player would.
	 */
	void launchGlide(final double x, final double height, final double z, final float yaw, final float pitch) {
		this.teleport(x, this.groundY + height, z, yaw, pitch);
		this.context.waitTicks(2);
		this.pressJump();
		this.context.waitFor(client -> client.player.isFallFlying(), SHORT_TIMEOUT);
	}

	boolean gliding() {
		return this.client(client -> client.player.isFallFlying());
	}

	boolean serverGliding() {
		return this.server.computeOnServer(server -> serverPlayer(server).isFallFlying());
	}

	Vec3 position() {
		return this.client(client -> client.player.position());
	}

	Vec3 velocity() {
		return this.client(client -> client.player.getDeltaMovement());
	}

	double height() {
		return this.position().y - this.groundY;
	}

	ItemStack chestItem() {
		return this.client(client -> client.player.getItemBySlot(EquipmentSlot.CHEST).copy());
	}

	ItemStack serverChestItem() {
		return this.server.computeOnServer(server -> serverPlayer(server).getItemBySlot(EquipmentSlot.CHEST).copy());
	}

	/**
	 * Waits up to {@code ticks} ticks and returns whether {@code condition} held in every one of them.
	 */
	boolean holdsFor(final int ticks, final Predicate<Minecraft> condition) {
		for (int tick = 0; tick < ticks; tick++) {
			this.context.waitTick();
			if (!this.client(condition::test)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Waits up to {@code ticks} ticks for {@code condition} and returns the number of ticks it took, or -1.
	 */
	int ticksUntil(final int ticks, final Predicate<Minecraft> condition) {
		for (int tick = 0; tick <= ticks; tick++) {
			if (this.client(condition::test)) {
				return tick;
			}
			this.context.waitTick();
		}
		return -1;
	}

	/**
	 * Waits until the server sees the local player's glide state as {@code gliding}.
	 */
	void checkServerGliding(final boolean gliding, final String message) {
		for (int tick = 0; tick < SHORT_TIMEOUT; tick++) {
			if (this.serverGliding() == gliding) {
				return;
			}
			this.context.waitTick();
		}
		check(false, message);
	}

	/** The local player's chest item; call on the client thread. */
	static ItemStack chest(final Minecraft client) {
		return client.player.getItemBySlot(EquipmentSlot.CHEST);
	}

	static ServerPlayer serverPlayer(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static boolean isClientGliding(final Minecraft client) {
		return ElytraClient.glide().isClientGliding(client.player);
	}
}
