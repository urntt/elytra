package com.urntt.elytra.config;

import com.mojang.serialization.Codec;
import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import com.urntt.elytra.FeatureController;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Configuration screen built from vanilla widgets. Every change is saved immediately.
 */
public final class ElytraConfigScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("options.elytra.title");

	private final Map<Feature, OptionInstance<Boolean>> featureOptions = new EnumMap<>(Feature.class);
	/** Set while the screen updates options to match the configuration, so their listeners do not act on it. */
	private boolean syncing;

	public ElytraConfigScreen(final @Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options, TITLE);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		ElytraConfig config = ElytraClient.config();
		this.featureOptions.clear();

		this.list.addHeader(Component.translatable("options.elytra.section.current"));
		this.list.addBig(toggle(ElytraClient.MAIN_SWITCH_NAME_KEY, config.isEnabled(), config::setEnabled));

		this.list.addHeader(Component.translatable("options.elytra.section.defaults"));
		this.list.addSmall(
				toggle("options.elytra.singleplayer_default", config.singleplayerDefault(), config::setSingleplayerDefault),
				toggle("options.elytra.multiplayer_default", config.multiplayerDefault(), config::setMultiplayerDefault));

		this.list.addHeader(Component.translatable("options.elytra.section.reset"));
		this.list.addSmall(
				toggle("options.elytra.reset_on_world_exit", config.resetOnWorldExit(), config::setResetOnWorldExit),
				toggle("options.elytra.reset_on_game_exit", config.resetOnGameExit(), config::setResetOnGameExit));

		this.list.addHeader(Component.translatable("options.elytra.section.gliding"));
		this.addFeatures(Feature.FAKE_ELYTRA, Feature.NO_GLIDING, Feature.GROUND_GLIDE, Feature.INSTANT_LANDING,
				Feature.INSTANT_FLY, Feature.STOP_IN_WATER, Feature.KEEP_POSE, Feature.INSTANT_STOP);

		this.list.addHeader(Component.translatable("options.elytra.section.flight_control"));
		this.addFeatures(Feature.FULLY_CONTROLLED);
		this.addTunings(Tuning.FULLY_CONTROLLED_HORIZONTAL_SPEED, Tuning.FULLY_CONTROLLED_VERTICAL_SPEED);
		this.addFeatures(Feature.PARTIALLY_CONTROLLED);
		this.addTunings(Tuning.PARTIALLY_CONTROLLED_HORIZONTAL_ACCELERATION, Tuning.PARTIALLY_CONTROLLED_MAX_SPEED,
				Tuning.PARTIALLY_CONTROLLED_ASCEND_ACCELERATION, Tuning.PARTIALLY_CONTROLLED_DESCEND_ACCELERATION,
				Tuning.PARTIALLY_CONTROLLED_DESCENT_SCALE, Tuning.PARTIALLY_CONTROLLED_ACCELERATION_SCALE);
		this.addFeatures(Feature.AUTOPILOT, Feature.ELYTRA_BOOST);
		this.addTunings(Tuning.ELYTRA_BOOST_DURATION);
		this.addFeatures(Feature.NO_CRASH);

		this.list.addHeader(Component.translatable("options.elytra.section.equipment"));
		this.addFeatures(Feature.ELYTRA_REPLACE);
		this.addTunings(Tuning.ELYTRA_REPLACE_MIN_DURABILITY);
		this.addFeatures(Feature.CHEST_SWAP);
		this.list.addSmall(
				toggle("options.elytra.chest_swap.on_jump", config.chestSwapOnJump(), config::setChestSwapOnJump),
				toggle("options.elytra.chest_swap.back", config.chestSwapBack(), config::setChestSwapBack));

		this.list.addHeader(Component.translatable("options.elytra.section.multiplayer"));
		this.list.addHeader(Component.translatable("options.elytra.multiplayer_warning").withStyle(ChatFormatting.YELLOW));
		this.list.addBig(new OptionInstance<>(
				"options.elytra.multiplayer_mode",
				mode -> Tooltip.create(mode.description().copy()
						.append("\n\n")
						.append(Component.translatable("options.elytra.multiplayer_mode.tooltip"))),
				// The button itself prepends the caption, so this only names the value.
				(caption, mode) -> mode.label(),
				new OptionInstance.Enum<>(List.of(MultiplayerMode.values()), MultiplayerMode.CODEC),
				config.multiplayerMode(),
				config::setMultiplayerMode));
		this.list.addBig(Button.builder(Component.translatable("options.elytra.edit_servers"),
						button -> this.minecraft.gui.setScreen(new ServerListScreen(this, config)))
				.tooltip(Tooltip.create(Component.translatable("options.elytra.edit_servers.tooltip")))
				.build());
	}

	private void addFeatures(final Feature... features) {
		FeatureController controller = ElytraClient.features();
		for (Feature feature : features) {
			OptionInstance<Boolean> option = toggle(feature.nameKey(), ElytraClient.config().isEnabled(feature), enabled -> {
				if (!this.syncing) {
					controller.setEnabled(feature, enabled);
					this.syncFeatureOptions();
				}
			});
			this.featureOptions.put(feature, option);
			this.list.addBig(option);
		}
	}

	private void addTunings(final Tuning... tunings) {
		this.list.addSmall(Arrays.stream(tunings).map(ElytraConfigScreen::slider).toArray(OptionInstance[]::new));
	}

	/**
	 * Shows the saved state on every feature button, after turning one feature on turned contradicting ones off.
	 */
	@SuppressWarnings("unchecked")
	private void syncFeatureOptions() {
		if (this.list == null) {
			return;
		}
		this.syncing = true;
		try {
			for (Map.Entry<Feature, OptionInstance<Boolean>> entry : this.featureOptions.entrySet()) {
				boolean enabled = ElytraClient.config().isEnabled(entry.getKey());
				entry.getValue().set(enabled);
				AbstractWidget widget = this.list.findOption(entry.getValue());
				if (widget instanceof CycleButton<?> button) {
					((CycleButton<Boolean>) button).setValue(enabled);
				}
			}
		} finally {
			this.syncing = false;
		}
	}

	/**
	 * Creates an on/off option whose tooltip is the translation of {@code captionKey + ".tooltip"}.
	 */
	private static OptionInstance<Boolean> toggle(final String captionKey, final boolean value,
			final Consumer<Boolean> onChange) {
		return OptionInstance.createBoolean(
				captionKey,
				OptionInstance.cachedConstantTooltip(Component.translatable(captionKey + ".tooltip")),
				value,
				onChange::accept);
	}

	/**
	 * Creates a slider over the values {@code tuning} accepts, whose tooltip is the translation of its caption key
	 * plus {@code ".tooltip"}. The value is saved once the slider is released.
	 */
	private static OptionInstance<Double> slider(final Tuning tuning) {
		ElytraConfig config = ElytraClient.config();
		return new OptionInstance<>(
				tuning.captionKey(),
				OptionInstance.cachedConstantTooltip(Component.translatable(tuning.captionKey() + ".tooltip")),
				(caption, value) -> Options.genericValueLabel(caption, tuning.formatValue(value)),
				new OptionInstance.IntRange(0, tuning.steps(), false).xmap(tuning::valueAt, tuning::stepOf, true),
				Codec.DOUBLE,
				config.get(tuning),
				value -> config.set(tuning, value));
	}
}
