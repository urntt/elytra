package com.urntt.elytra.flight;

import com.urntt.elytra.Feature;
import com.urntt.elytra.FeatureController;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * No Crash: keeps a glide from flying into a wall or into a chunk the client has not loaded.
 *
 * <p>Every tick it casts rays from the corners and the center of the player's hitbox along the glide's velocity. When
 * a ray hits the side of a block, or the path crosses into an unloaded chunk, the velocity toward that obstacle is
 * reduced so that the player covers at most a {@value #BRAKING_TICKS}th of the remaining gap per tick. The player
 * slows down smoothly, keeps moving along the obstacle, and stops {@value #MARGIN} blocks in front of it. Floors and
 * ceilings are not obstacles, so landing still works.
 */
public final class CrashGuard {
	/** The player covers at most this fraction (1/n) of the remaining gap per tick. */
	static final int BRAKING_TICKS = 4;
	/** Distance, in blocks, kept between the player and an obstacle. */
	static final double MARGIN = 0.05;
	/** How far inside the hitbox the rays start, so they never start inside a block the player touches. */
	private static final double RAY_INSET = 1.0E-3;

	private final FeatureController features;

	public CrashGuard(final FeatureController features) {
		this.features = features;
	}

	/**
	 * Returns the movement the player may make this tick instead of {@code movement}.
	 */
	public Vec3 limit(final LocalPlayer player, final Vec3 movement) {
		if (!this.features.isActive(Feature.NO_CRASH) || movement.lengthSqr() < 1.0E-12) {
			return movement;
		}
		Vec3 limited = limitByWalls(player, movement);
		return limitByUnloadedChunks(player.level(), player.getBoundingBox(), limited);
	}

	private static Vec3 limitByWalls(final LocalPlayer player, final Vec3 movement) {
		double speed = movement.length();
		Vec3 direction = movement.scale(1.0 / speed);
		double reach = speed * BRAKING_TICKS + MARGIN;

		Map<Direction, Double> gaps = new EnumMap<>(Direction.class);
		for (Vec3 start : rayStarts(player.getBoundingBox().deflate(RAY_INSET))) {
			BlockHitResult hit = player.level().clip(new ClipContext(start, start.add(direction.scale(reach)),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (hit.getType() != HitResult.Type.BLOCK || !hit.getDirection().getAxis().isHorizontal()) {
				continue;
			}
			double approach = -direction.dot(hit.getDirection().getUnitVec3());
			if (approach > 0.0) {
				gaps.merge(hit.getDirection(), hit.getLocation().distanceTo(start) * approach, Math::min);
			}
		}

		Vec3 result = movement;
		for (Map.Entry<Direction, Double> gap : gaps.entrySet()) {
			result = brake(result, gap.getKey().getUnitVec3(), gap.getValue());
		}
		return result;
	}

	private static Vec3 limitByUnloadedChunks(final Level level, final AABB box, final Vec3 movement) {
		Vec3 result = movement;
		for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
			double component = movement.get(axis);
			if (component == 0.0) {
				continue;
			}
			Direction towards = Direction.fromAxisAndDirection(axis,
					component > 0.0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
			double gap = gapToUnloadedChunk(level, box, towards, Math.abs(component) * BRAKING_TICKS + MARGIN);
			if (gap < Double.POSITIVE_INFINITY) {
				result = brake(result, towards.getOpposite().getUnitVec3(), gap);
			}
		}

		// Moving diagonally can enter a chunk that neither axis alone runs into.
		if (touchesUnloadedChunk(level, box.move(result)) && !touchesUnloadedChunk(level, box)) {
			result = new Vec3(0.0, result.y, 0.0);
		}
		return result;
	}

	/**
	 * Reduces the part of {@code movement} that approaches a surface with outward normal {@code normal}, at distance
	 * {@code gap}, to a {@value #BRAKING_TICKS}th of the gap left after the margin.
	 */
	private static Vec3 brake(final Vec3 movement, final Vec3 normal, final double gap) {
		double approachSpeed = -movement.dot(normal);
		double allowed = Math.max(0.0, gap - MARGIN) / BRAKING_TICKS;
		return approachSpeed > allowed ? movement.add(normal.scale(approachSpeed - allowed)) : movement;
	}

	/**
	 * Returns the distance from the side of {@code box} facing {@code towards} to the nearest boundary of an unloaded
	 * chunk within {@code reach} blocks in that direction, or infinity if there is none.
	 */
	private static double gapToUnloadedChunk(final Level level, final AABB box, final Direction towards, final double reach) {
		Direction.Axis axis = towards.getAxis();
		Direction.Axis across = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		boolean positive = towards.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		double front = positive ? box.max(axis) : box.min(axis);
		int firstAcross = SectionPos.blockToSectionCoord(box.min(across));
		int lastAcross = SectionPos.blockToSectionCoord(box.max(across));
		int current = SectionPos.blockToSectionCoord(front);
		int last = SectionPos.blockToSectionCoord(front + (positive ? reach : -reach));

		for (int chunk = current; positive ? chunk <= last : chunk >= last; chunk += positive ? 1 : -1) {
			if (chunk == current) {
				continue;
			}
			for (int other = firstAcross; other <= lastAcross; other++) {
				boolean loaded = axis == Direction.Axis.X
						? level.getChunkSource().hasChunk(chunk, other)
						: level.getChunkSource().hasChunk(other, chunk);
				if (!loaded) {
					double boundary = SectionPos.sectionToBlockCoord(positive ? chunk : chunk + 1);
					return Math.abs(boundary - front);
				}
			}
		}
		return Double.POSITIVE_INFINITY;
	}

	private static boolean touchesUnloadedChunk(final Level level, final AABB box) {
		for (int x = SectionPos.blockToSectionCoord(box.minX); x <= SectionPos.blockToSectionCoord(box.maxX); x++) {
			for (int z = SectionPos.blockToSectionCoord(box.minZ); z <= SectionPos.blockToSectionCoord(box.maxZ); z++) {
				if (!level.getChunkSource().hasChunk(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The eight corners and the center of {@code box}. */
	private static List<Vec3> rayStarts(final AABB box) {
		return List.of(
				new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
				new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
				new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
				new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ),
				box.getCenter());
	}
}
