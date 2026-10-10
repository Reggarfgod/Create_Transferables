package com.reggarf.mods.transferables.client.portal.kinetics;

import javax.annotation.Nullable;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.content.portal.kinetics.PortalShaftLink;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;

import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.lib.model.Models;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Creates the same {@link AllPartialModels#SHAFT_HALF} Flywheel instance Create uses on motors /
 * encased cogs, placed in the portal block so rotation stays locked to the kinetic BE speed.
 */
public final class PortalShaftHalfInstances {
	private PortalShaftHalfInstances() {}

	@Nullable
	public static Direction portalFacing(KineticBlockEntity be) {
		if (!(be instanceof PortalAccess access))
			return null;
		if (PortalShaftLink.getPortalShaftAxis(be.getBlockState()) == null)
			return null;

		Direction bound = access.transferables$getPortalDirection();
		if (access.transferables$isPortalConnected() && bound != null)
			return bound;

		// Before client sync / while linking: still show the stub when geometrically facing a portal.
		Level level = be.getLevel();
		if (level == null)
			return null;
		Direction.Axis axis = PortalShaftLink.getPortalShaftAxis(be.getBlockState());
		if (axis == null)
			return null;
		for (Direction towardPortal : Iterate.directionsInAxis(axis)) {
			if (PortalShaftLink.isShaftFacingPortal(level, be.getBlockPos(), towardPortal))
				return towardPortal;
		}
		return null;
	}

	/** Direction the half-shaft model should face: from the portal back toward the real shaft. */
	public static Direction modelFacing(Direction towardPortal) {
		return towardPortal.getOpposite();
	}

	/** World position of the portal block (used for lighting lookup). */
	public static BlockPos portalPos(KineticBlockEntity be, Direction towardPortal) {
		return be.getBlockPos().relative(towardPortal);
	}

	/** Position relative to the visualization origin. */
	public static BlockPos portalVisualPos(BlockPos visualPos, Direction towardPortal) {
		return visualPos.relative(towardPortal);
	}

	public static RotatingInstance create(InstancerProvider provider, KineticBlockEntity be, BlockPos visualPos, Direction towardPortal) {
		// shaft_half is authored along +Z (SOUTH), same as motors / encased cog stubs.
		Direction facing = modelFacing(towardPortal);
		RotatingInstance instance = provider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
				.createInstance();
		instance.setup(be)
				.setPosition(portalVisualPos(visualPos, towardPortal))
				.rotateToFace(Direction.SOUTH, facing)
				.setChanged();
		return instance;
	}

	public static RotatingInstance create(InstancerProvider provider, KineticBlockEntity be, Direction towardPortal) {
		return create(provider, be, be.getBlockPos(), towardPortal);
	}

	/** Speed/light sync only — do not call {@code rotateToFace} again (it accumulates on the quaternion). */
	public static void sync(RotatingInstance instance, KineticBlockEntity be, BlockPos visualPos, Direction towardPortal) {
		instance.setup(be)
				.setPosition(portalVisualPos(visualPos, towardPortal))
				.setChanged();
	}

	public static void sync(RotatingInstance instance, KineticBlockEntity be, Direction towardPortal) {
		sync(instance, be, be.getBlockPos(), towardPortal);
	}
}
