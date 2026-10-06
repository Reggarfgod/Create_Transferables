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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Creates the same {@link AllPartialModels#SHAFT_HALF} Flywheel instance Create uses on motors /
 * encased cogs, placed in the portal block so rotation stays locked to the kinetic BE speed.
 */
public final class PortalShaftHalfInstances {
	private PortalShaftHalfInstances() {}

	@Nullable
	public static Direction portalFacing(KineticBlockEntity be) {
		if (!(be instanceof PortalAccess access) || !access.transferables$isPortalConnected())
			return null;
		if (PortalShaftLink.getPortalShaftAxis(be.getBlockState()) == null)
			return null;
		return access.transferables$getPortalDirection();
	}

	/** Direction the half-shaft model should face: from the portal back toward the real shaft. */
	public static Direction modelFacing(Direction towardPortal) {
		return towardPortal.getOpposite();
	}

	public static BlockPos portalPos(KineticBlockEntity be, Direction towardPortal) {
		return be.getBlockPos().relative(towardPortal);
	}

	public static RotatingInstance create(InstancerProvider provider, KineticBlockEntity be, Direction towardPortal) {
		// shaft_half is authored along +Z (SOUTH), same as motors / encased cog stubs.
		Direction facing = modelFacing(towardPortal);
		RotatingInstance instance = provider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
				.createInstance();
		instance.setup(be)
				.setPosition(portalPos(be, towardPortal))
				.rotateToFace(Direction.SOUTH, facing)
				.setChanged();
		return instance;
	}

	/** Speed/light sync only — do not call {@code rotateToFace} again (it accumulates on the quaternion). */
	public static void sync(RotatingInstance instance, KineticBlockEntity be, Direction towardPortal) {
		instance.setup(be)
				.setPosition(portalPos(be, towardPortal))
				.setChanged();
	}
}
