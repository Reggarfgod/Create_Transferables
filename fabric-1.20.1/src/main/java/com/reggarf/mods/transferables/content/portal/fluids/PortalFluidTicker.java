package com.reggarf.mods.transferables.content.portal.fluids;

import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps portal-bound exit pipes transferring even if Create skipped a manageSource pass.
 */
public final class PortalFluidTicker {
	private PortalFluidTicker() {}

	public static void tick(FluidTransportBehaviour node, ServerLevel level) {
		if (!(node instanceof PortalPumpAccess access) || !access.transferables$isPumpPortalConnected())
			return;

		Direction towardPortal = access.transferables$getPumpPortalDirection();
		if (towardPortal == null)
			return;
		if (!PortalFluidLink.isFacingPortal(level, node.getPos(), towardPortal))
			return;

		PipeConnection portalConn = node.getConnection(towardPortal);
		if (portalConn == null)
			return;

		// Triggers PipeConnectionMixin to re-attach PortalFlowSource, then runs transfer
		portalConn.manageSource(level, node.getPos());
	}
}
