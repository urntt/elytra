package com.urntt.elytra.inventory;

import com.urntt.elytra.Feature;
import com.urntt.elytra.FeatureController;
import com.urntt.elytra.config.ElytraConfig;
import com.urntt.elytra.config.Tuning;
import java.util.Comparator;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/**
 * Moves elytras and chestplates between the inventory and the chest slot for Chest Swap and Elytra Replace.
 *
 * <p>Items are moved with ordinary inventory clicks in the player's own inventory menu, the same packets a player
 * sends by clicking the slots, so the server applies and validates them as usual. Nothing is moved while another
 * container is open or an item is held on the cursor.
 */
public final class ElytraEquipment {
	/** The chest armor slot of {@link InventoryMenu}, which lists head, chest, legs, and feet in that order. */
	public static final int CHEST_SLOT = InventoryMenu.ARMOR_SLOT_START + 1;
	/** Ticks to wait after Elytra Replace moved items, so the server's answer arrives before it checks again. */
	private static final int REPLACE_COOLDOWN_TICKS = 10;

	/** Outcome of {@link #swapManually(LocalPlayer)}. */
	public enum SwapResult {
		EQUIPPED_ELYTRA,
		EQUIPPED_CHESTPLATE,
		NO_ELYTRA,
		NO_CHESTPLATE,
		/** Another container is open, an item is on the cursor, or the chest item cannot be taken off. */
		UNAVAILABLE;

		public @Nullable Component message() {
			return switch (this) {
				case EQUIPPED_ELYTRA, EQUIPPED_CHESTPLATE -> null;
				case NO_ELYTRA -> Component.translatable("message.elytra.no_elytra");
				case NO_CHESTPLATE -> Component.translatable("message.elytra.no_chestplate");
				case UNAVAILABLE -> Component.translatable("message.elytra.swap_unavailable");
			};
		}
	}

	private final FeatureController features;
	private final ElytraConfig config;
	private @Nullable LocalPlayer player;
	/** Whether Chest Swap put on an elytra for the current glide and should take it off when the glide ends. */
	private boolean swappedForGlide;
	/** The menu slot the chestplate went to when Chest Swap put on an elytra. */
	private int chestplateSlot = -1;
	private int replaceCooldown;

	public ElytraEquipment(final FeatureController features, final ElytraConfig config) {
		this.features = features;
		this.config = config;
	}

	/**
	 * Returns whether {@code player} wears an item it can glide with, as vanilla checks it.
	 */
	public static boolean hasUsableGlider(final Player player) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (LivingEntity.canGlideUsing(player.getItemBySlot(slot), slot)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Called when the player tries to start gliding without a usable glider. With Chest Swap's "swap on jump", puts
	 * on the most durable elytra from the inventory, so vanilla can start the glide right after.
	 */
	public void equipElytraForGlide(final LocalPlayer player) {
		this.bind(player);
		if (!this.features.isActive(Feature.CHEST_SWAP) || !this.config.chestSwapOnJump() || hasUsableGlider(player)
				|| !canMoveItems(player)) {
			return;
		}
		OptionalInt elytra = bestElytra(player, 1);
		if (elytra.isPresent()) {
			int slot = elytra.getAsInt();
			boolean hadChestplate = isChestplate(player.inventoryMenu.getSlot(CHEST_SLOT).getItem());
			swapWithChest(player, slot);
			this.swappedForGlide = true;
			this.chestplateSlot = hadChestplate ? slot : -1;
		}
	}

	/**
	 * Swaps between an elytra and a chestplate on the swap key.
	 */
	public SwapResult swapManually(final LocalPlayer player) {
		this.bind(player);
		if (!canMoveItems(player)) {
			return SwapResult.UNAVAILABLE;
		}
		this.swappedForGlide = false;
		if (player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
			OptionalInt chestplate = this.chestplate(player);
			if (chestplate.isEmpty()) {
				return SwapResult.NO_CHESTPLATE;
			}
			swapWithChest(player, chestplate.getAsInt());
			return SwapResult.EQUIPPED_CHESTPLATE;
		}
		OptionalInt elytra = bestElytra(player, 1);
		if (elytra.isEmpty()) {
			return SwapResult.NO_ELYTRA;
		}
		swapWithChest(player, elytra.getAsInt());
		return SwapResult.EQUIPPED_ELYTRA;
	}

	/**
	 * Called every tick after the player moved: puts the chestplate back after a glide that Chest Swap started, and
	 * replaces a worn-out elytra.
	 */
	public void tick(final LocalPlayer player) {
		this.bind(player);
		if (this.replaceCooldown > 0) {
			this.replaceCooldown--;
		}

		if (this.swappedForGlide && !player.isFallFlying()) {
			this.swappedForGlide = false;
			if (this.features.isActive(Feature.CHEST_SWAP) && this.config.chestSwapBack() && canMoveItems(player)
					&& player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
				this.chestplate(player).ifPresent(slot -> swapWithChest(player, slot));
			}
		}

		if (this.replaceCooldown == 0 && this.features.isActive(Feature.ELYTRA_REPLACE) && canMoveItems(player)) {
			ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
			int minDurability = (int) this.config.get(Tuning.ELYTRA_REPLACE_MIN_DURABILITY);
			if (worn.has(DataComponents.GLIDER) && remainingDurability(worn) <= minDurability) {
				OptionalInt spare = bestElytra(player, minDurability + 1);
				if (spare.isPresent()) {
					swapWithChest(player, spare.getAsInt());
					this.replaceCooldown = REPLACE_COOLDOWN_TICKS;
					player.sendOverlayMessage(Component.translatable("message.elytra.replaced"));
				}
			}
		}
	}

	private void bind(final LocalPlayer player) {
		if (this.player != player) {
			this.player = player;
			this.swappedForGlide = false;
			this.chestplateSlot = -1;
			this.replaceCooldown = 0;
		}
	}

	/**
	 * Finds the chestplate to put on: the one Chest Swap took off if it is still in the same slot, otherwise the
	 * first one in the inventory.
	 */
	private OptionalInt chestplate(final LocalPlayer player) {
		if (this.chestplateSlot >= 0 && isChestplate(player.inventoryMenu.getSlot(this.chestplateSlot).getItem())) {
			return OptionalInt.of(this.chestplateSlot);
		}
		return inventorySlots().filter(slot -> isChestplate(player.inventoryMenu.getSlot(slot).getItem())).findFirst();
	}

	/**
	 * Finds the inventory slot of the elytra with the most remaining durability, if it has at least
	 * {@code minDurability} left and vanilla would let the player glide with it.
	 */
	private static OptionalInt bestElytra(final LocalPlayer player, final int minDurability) {
		Predicate<ItemStack> usable = stack -> LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST)
				&& remainingDurability(stack) >= minDurability;
		return inventorySlots()
				.filter(slot -> usable.test(player.inventoryMenu.getSlot(slot).getItem()))
				.boxed()
				.max(Comparator.comparingInt(slot -> remainingDurability(player.inventoryMenu.getSlot(slot).getItem())))
				.map(OptionalInt::of)
				.orElse(OptionalInt.empty());
	}

	/** The main inventory and hotbar slots of {@link InventoryMenu}. */
	private static IntStream inventorySlots() {
		return IntStream.range(InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END);
	}

	private static boolean isChestplate(final ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.has(DataComponents.GLIDER);
	}

	private static int remainingDurability(final ItemStack stack) {
		return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
	}

	private static boolean canMoveItems(final LocalPlayer player) {
		return player.containerMenu == player.inventoryMenu
				&& player.inventoryMenu.getCarried().isEmpty()
				&& (player.inventoryMenu.getSlot(CHEST_SLOT).getItem().isEmpty()
						|| player.inventoryMenu.getSlot(CHEST_SLOT).mayPickup(player));
	}

	/**
	 * Exchanges the items in inventory slot {@code slot} and the chest slot with three clicks: pick up the item, click
	 * the chest slot to swap it with the worn item, and put that one down where the first item was.
	 */
	private static void swapWithChest(final LocalPlayer player, final int slot) {
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		if (gameMode == null) {
			return;
		}
		int containerId = player.inventoryMenu.containerId;
		gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
		gameMode.handleContainerInput(containerId, CHEST_SLOT, 0, ContainerInput.PICKUP, player);
		gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
	}
}
