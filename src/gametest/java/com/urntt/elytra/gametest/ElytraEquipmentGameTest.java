package com.urntt.elytra.gametest;

import static com.urntt.elytra.gametest.GameTestSupport.LOGGER;
import static com.urntt.elytra.gametest.GameTestSupport.bindKey;
import static com.urntt.elytra.gametest.GameTestSupport.check;
import static com.urntt.elytra.gametest.GameTestSupport.configure;
import static com.urntt.elytra.gametest.GameTestSupport.onlyEnable;
import static com.urntt.elytra.gametest.GameTestSupport.unbindKey;

import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import com.urntt.elytra.config.Tuning;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Checks Elytra Replace and Chest Swap in singleplayer, on the client and on the server.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ElytraEquipmentGameTest implements FabricClientGameTest {
	/** Marks the spare elytra in the check that should not swap it in. */
	private static final int SPARE_REPAIR_COST = 5;
	private static final String CHESTPLATE = "minecraft:diamond_chestplate";

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			FlightLab lab = new FlightLab(context, singleplayer.getServer());

			checkElytraReplace(context, lab);
			checkElytraReplaceDuringGlide(context, lab);
			checkChestSwapOnJump(context, lab);
			checkChestSwapKey(context, lab);
		}
		onlyEnable(context);
	}

	private static void checkElytraReplace(final ClientGameTestContext context, final FlightLab lab) {
		int maxDamage = new ItemStack(Items.ELYTRA).getMaxDamage();
		int minDurability = (int) Tuning.ELYTRA_REPLACE_MIN_DURABILITY.defaultValue();
		// Exactly the default minimum left, which already counts as worn out.
		int wornOutDamage = maxDamage - minDurability;
		onlyEnable(context);
		lab.resetPlayer(0, 0);
		lab.wear("minecraft:elytra[minecraft:damage=" + wornOutDamage + "]");
		lab.give("minecraft:elytra");
		check(lab.holdsFor(10, client -> FlightLab.chest(client).getDamageValue() == wornOutDamage),
				"without Elytra Replace the worn-out elytra should stay on");

		onlyEnable(context, Feature.ELYTRA_REPLACE);
		int ticks = lab.ticksUntil(5, client -> FlightLab.chest(client).getDamageValue() == 0);
		LOGGER.info("Elytra Replace swapped in the spare elytra after {} ticks", ticks);
		check(ticks >= 0, "Elytra Replace should put on the spare elytra");
		context.takeScreenshot("elytra-replace");
		context.waitTicks(5);
		ItemStack serverChest = lab.serverChestItem();
		check(serverChest.is(Items.ELYTRA) && serverChest.getDamageValue() == 0, "the server should see the spare elytra worn");
		check(lab.client(client -> client.player.getInventory().contains(stack -> stack.getDamageValue() == wornOutDamage)),
				"the worn-out elytra should be in the inventory");

		// A spare that is not above the minimum either is not worth swapping in. A repair cost tells the two apart.
		lab.resetPlayer(0, 0);
		lab.wear("minecraft:elytra[minecraft:damage=" + wornOutDamage + "]");
		lab.give("minecraft:elytra[minecraft:damage=" + wornOutDamage + ",minecraft:repair_cost=" + SPARE_REPAIR_COST + "]");
		check(lab.holdsFor(10, client -> FlightLab.chest(client).getOrDefault(DataComponents.REPAIR_COST, 0) != SPARE_REPAIR_COST),
				"Elytra Replace should not swap in a spare that is worn out too");
	}

	/**
	 * Lets an elytra wear down during a glide. An elytra with 1 durability left can no longer glide, so with a minimum
	 * of 1 the server ends the glide before the spare goes on, while with a minimum of 2 the swap keeps the glide going.
	 * The README and the setting's tooltip describe this.
	 */
	private static void checkElytraReplaceDuringGlide(final ClientGameTestContext context, final FlightLab lab) {
		int maxDamage = new ItemStack(Items.ELYTRA).getMaxDamage();
		for (int minDurability : new int[] {1, 2}) {
			onlyEnable(context, Feature.ELYTRA_REPLACE);
			configure(context, config -> config.set(Tuning.ELYTRA_REPLACE_MIN_DURABILITY, minDurability));
			lab.resetPlayer(0, 0);
			// One durability above the minimum, so the swap happens when the glide wears it down.
			lab.wear("minecraft:elytra[minecraft:damage=" + (maxDamage - minDurability - 1) + "]");
			lab.give("minecraft:elytra");
			lab.launchGlide(0, 60, 0, 0.0F, 0.0F);
			int ticks = lab.ticksUntil(FlightLab.LONG_TIMEOUT, client -> FlightLab.chest(client).getDamageValue() == 0);
			check(ticks >= 0, "Elytra Replace should swap in the spare when the glide wears the elytra down");
			context.waitTicks(5);
			boolean gliding = lab.gliding();
			LOGGER.info("Elytra Replace with a minimum of {} swapped {} ticks into the glide; still gliding: {}",
					minDurability, ticks, gliding);
			check(gliding == (minDurability > 1), minDurability > 1
					? "a minimum of 2 should swap without ending the glide"
					: "an elytra worn down to 1 durability should end the glide");
		}
	}

	/**
	 * With a chestplate on, jumping in the air puts on the elytra and glides; landing puts the chestplate back.
	 */
	private static void checkChestSwapOnJump(final ClientGameTestContext context, final FlightLab lab) {
		onlyEnable(context);
		lab.resetPlayer(0, 0);
		lab.wear(CHESTPLATE);
		lab.give("minecraft:elytra");
		lab.teleport(0, lab.groundY + 10, 0, 0.0F, 0.0F);
		context.waitTicks(2);
		lab.pressJump();
		check(lab.holdsFor(5, client -> !client.player.isFallFlying()), "without Chest Swap there is nothing to glide with");
		check(lab.chestItem().is(Items.DIAMOND_CHESTPLATE), "without Chest Swap the chestplate should stay on");

		onlyEnable(context, Feature.CHEST_SWAP);
		lab.resetPlayer(0, 0);
		lab.wear(CHESTPLATE);
		lab.give("minecraft:elytra");
		lab.launchGlide(0, 10, 0, 0.0F, 0.0F);
		check(lab.chestItem().is(Items.ELYTRA), "Chest Swap should put on the elytra");
		lab.checkServerGliding(true, "the server should glide with the swapped-in elytra");
		check(lab.serverChestItem().is(Items.ELYTRA), "the server should see the elytra worn");

		context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying(), FlightLab.LONG_TIMEOUT);
		int ticks = lab.ticksUntil(5, client -> FlightLab.chest(client).is(Items.DIAMOND_CHESTPLATE));
		LOGGER.info("Chest Swap put the chestplate back {} ticks after landing", ticks);
		check(ticks >= 0, "Chest Swap should put the chestplate back after landing");
		context.waitTicks(5);
		check(lab.serverChestItem().is(Items.DIAMOND_CHESTPLATE), "the server should see the chestplate worn again");

		configure(context, config -> config.setChestSwapBack(false));
		lab.launchGlide(0, 10, 0, 0.0F, 0.0F);
		context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying(), FlightLab.LONG_TIMEOUT);
		check(lab.holdsFor(5, client -> FlightLab.chest(client).is(Items.ELYTRA)), "without Swap Back the elytra should stay on");
	}

	private static void checkChestSwapKey(final ClientGameTestContext context, final FlightLab lab) {
		KeyMapping swapKey = bindKey(context, ElytraClient.SWAP_KEY_NAME, "key.keyboard.j");
		onlyEnable(context, Feature.CHEST_SWAP);
		lab.resetPlayer(0, 0);
		lab.wear(CHESTPLATE);
		lab.give("minecraft:elytra");

		context.getInput().pressKey(swapKey);
		context.waitTicks(2);
		check(lab.chestItem().is(Items.ELYTRA), "the swap key should put on the elytra");
		context.getInput().pressKey(swapKey);
		context.waitTicks(2);
		check(lab.chestItem().is(Items.DIAMOND_CHESTPLATE), "the swap key should put the chestplate back on");
		context.waitTicks(5);
		check(lab.serverChestItem().is(Items.DIAMOND_CHESTPLATE), "the server should agree with the swaps");

		lab.server.runCommand("clear @a minecraft:elytra");
		context.waitTicks(2);
		context.getInput().pressKey(swapKey);
		context.waitTicks(2);
		check(lab.chestItem().is(Items.DIAMOND_CHESTPLATE), "without an elytra the swap key should change nothing");
		context.takeScreenshot("elytra-swap-no-elytra");
		unbindKey(context, swapKey);
	}
}
