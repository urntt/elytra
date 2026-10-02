package com.urntt.elytra.config;

import com.urntt.elytra.Feature;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The numeric settings, with their defaults and the ranges the configuration screen offers. Velocities are in blocks
 * per tick, accelerations in blocks per tick added each tick, as the game itself measures them.
 */
public enum Tuning {
	FULLY_CONTROLLED_HORIZONTAL_SPEED("fully_controlled.horizontal_speed", Feature.FULLY_CONTROLLED, Unit.BLOCKS_PER_TICK,
			1.0, 0.05, 5.0, 0.05),
	FULLY_CONTROLLED_VERTICAL_SPEED("fully_controlled.vertical_speed", Feature.FULLY_CONTROLLED, Unit.BLOCKS_PER_TICK,
			0.5, 0.05, 3.0, 0.05),
	PARTIALLY_CONTROLLED_HORIZONTAL_ACCELERATION("partially_controlled.horizontal_acceleration",
			Feature.PARTIALLY_CONTROLLED, Unit.BLOCKS_PER_TICK_PER_TICK, 0.05, 0.0, 0.5, 0.01),
	PARTIALLY_CONTROLLED_ASCEND_ACCELERATION("partially_controlled.ascend_acceleration", Feature.PARTIALLY_CONTROLLED,
			Unit.BLOCKS_PER_TICK_PER_TICK, 0.08, 0.0, 0.5, 0.01),
	PARTIALLY_CONTROLLED_DESCEND_ACCELERATION("partially_controlled.descend_acceleration", Feature.PARTIALLY_CONTROLLED,
			Unit.BLOCKS_PER_TICK_PER_TICK, 0.04, 0.0, 0.5, 0.01),
	/** Multiplier of the gravity that pulls a glide down. */
	PARTIALLY_CONTROLLED_DESCENT_SCALE("partially_controlled.descent_scale", Feature.PARTIALLY_CONTROLLED, Unit.PERCENT,
			1.0, 0.0, 2.0, 0.05),
	/** Multiplier of the speed a glide gains by turning height into forward speed. */
	PARTIALLY_CONTROLLED_ACCELERATION_SCALE("partially_controlled.acceleration_scale", Feature.PARTIALLY_CONTROLLED,
			Unit.PERCENT, 1.0, 0.0, 3.0, 0.05),
	PARTIALLY_CONTROLLED_MAX_SPEED("partially_controlled.max_speed", Feature.PARTIALLY_CONTROLLED, Unit.BLOCKS_PER_TICK,
			3.0, 0.5, 10.0, 0.1),
	ELYTRA_REPLACE_MIN_DURABILITY("elytra_replace.min_durability", Feature.ELYTRA_REPLACE, Unit.DURABILITY,
			10, 1, 100, 1),
	ELYTRA_BOOST_DURATION("elytra_boost.duration", Feature.ELYTRA_BOOST, Unit.TICKS, 30, 1, 200, 1);

	public enum Unit {
		BLOCKS_PER_TICK,
		BLOCKS_PER_TICK_PER_TICK,
		PERCENT,
		DURABILITY,
		TICKS;

		/** Formats a value for a slider label. Tooltips explain the units that the label leaves out to stay short. */
		Component format(final double value) {
			return switch (this) {
				case BLOCKS_PER_TICK, BLOCKS_PER_TICK_PER_TICK -> Component.literal("%.2f".formatted(value));
				case PERCENT -> Component.translatable("options.elytra.unit.percent", Math.round(value * 100.0));
				case DURABILITY -> Component.literal(Long.toString(Math.round(value)));
				case TICKS -> Component.translatable("options.elytra.unit.ticks", Math.round(value));
			};
		}
	}

	/** All ranges and steps are multiples of 0.01, so six decimals represent every value exactly. */
	private static final double VALUE_PRECISION = 1.0E6;

	private final String id;
	private final Feature feature;
	private final Unit unit;
	private final double defaultValue;
	private final double min;
	private final double max;
	private final double step;

	Tuning(final String id, final Feature feature, final Unit unit, final double defaultValue, final double min,
			final double max, final double step) {
		this.id = id;
		this.feature = feature;
		this.unit = unit;
		this.defaultValue = defaultValue;
		this.min = min;
		this.max = max;
		this.step = step;
	}

	/** The name used in the configuration file and in translation keys. */
	public String id() {
		return this.id;
	}

	/** The feature this setting belongs to. */
	public Feature feature() {
		return this.feature;
	}

	public double defaultValue() {
		return this.defaultValue;
	}

	/** Number of slider steps above the minimum. */
	public int steps() {
		return (int) Math.round((this.max - this.min) / this.step);
	}

	public double valueAt(final int step) {
		double value = this.min + Mth.clamp(step, 0, this.steps()) * this.step;
		// Drop floating-point noise such as 1.0000000000000002, so values stay readable in the config file.
		return Math.round(value * VALUE_PRECISION) / VALUE_PRECISION;
	}

	public int stepOf(final double value) {
		return Mth.clamp((int) Math.round((value - this.min) / this.step), 0, this.steps());
	}

	/** Rounds {@code value} to the nearest value the setting accepts. */
	public double normalize(final double value) {
		return Double.isFinite(value) ? this.valueAt(this.stepOf(value)) : this.defaultValue;
	}

	public String captionKey() {
		return "options.elytra.tuning." + this.id;
	}

	public Component formatValue(final double value) {
		return this.unit.format(value);
	}
}
