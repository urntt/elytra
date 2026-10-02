package com.urntt.elytra.flight;

import com.urntt.elytra.config.ElytraConfig;
import com.urntt.elytra.config.Tuning;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Elytra Boost: spawns a firework rocket on the client only, attached to the gliding player, so vanilla's rocket
 * code pushes the player exactly as a used rocket would. The server never sees the rocket, and no rocket item is
 * used up.
 *
 * <p>A client-side rocket never explodes (only the server explodes rockets), so this class removes each one after the
 * configured boost duration.
 */
public final class ElytraBoost {
	/** Client-side rockets get negative ids, which the server never assigns, so they cannot replace server entities. */
	private static int nextRocketId = -1_000_000;

	private final ElytraConfig config;
	private final List<Rocket> rockets = new ArrayList<>();

	public ElytraBoost(final ElytraConfig config) {
		this.config = config;
	}

	/**
	 * Attaches a new rocket to {@code player} if it is gliding.
	 *
	 * @return whether a rocket was launched
	 */
	public boolean launch(final LocalPlayer player) {
		if (!player.isFallFlying() || !(player.level() instanceof ClientLevel level)) {
			return false;
		}
		FireworkRocketEntity rocket = new FireworkRocketEntity(level, new ItemStack(Items.FIREWORK_ROCKET), player);
		rocket.setId(nextRocketId--);
		// Vanilla plays the launch sound from the server; a client-side rocket plays it here.
		rocket.setSilent(true);
		level.playLocalSound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0F, 1.0F);
		level.addEntity(rocket);
		this.rockets.add(new Rocket(rocket, (int) this.config.get(Tuning.ELYTRA_BOOST_DURATION)));
		return true;
	}

	/** Returns how many rockets are boosting the player now. */
	public int activeRockets() {
		return this.rockets.size();
	}

	/**
	 * Called at the end of every client tick. Each rocket has pushed the player once per tick since it was launched;
	 * rockets whose duration is over are removed.
	 */
	public void tick() {
		for (Iterator<Rocket> iterator = this.rockets.iterator(); iterator.hasNext(); ) {
			Rocket rocket = iterator.next();
			rocket.remainingTicks--;
			if (rocket.remainingTicks <= 0 || rocket.entity.isRemoved()) {
				rocket.entity.discard();
				iterator.remove();
			}
		}
	}

	/** Forgets all rockets, for example when leaving the world, which removes them with it. */
	public void clear() {
		this.rockets.clear();
	}

	private static final class Rocket {
		private final FireworkRocketEntity entity;
		private int remainingTicks;

		private Rocket(final FireworkRocketEntity entity, final int remainingTicks) {
			this.entity = entity;
			this.remainingTicks = remainingTicks;
		}
	}
}
