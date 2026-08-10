package com.reggarf.mods.transferables.api;

import javax.annotation.Nullable;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.api.registry.SimpleRegistry;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shaft version of Create's PortalTrackProvider.
 * It keeps shaft portal logic separate while still falling back to Create's
 * registered portal-track providers for vanilla Nether portals and compat portals.
 */
@FunctionalInterface
public interface PortalProvider {
	SimpleRegistry<Block, PortalProvider> REGISTRY = SimpleRegistry.create();

	@Nullable
	Exit findExit(ServerLevel level, BlockFace face);

	static boolean isSupportedPortal(BlockState state) {
		return REGISTRY.get(state) != null || PortalTrackProvider.isSupportedPortal(state);
	}

	@Nullable
	static Exit getOtherSide(ServerLevel level, BlockFace inboundShaft) {
		BlockPos portalPos = inboundShaft.getConnectedPos();
		BlockState portalState = level.getBlockState(portalPos);

		PortalProvider shaftProvider = REGISTRY.get(portalState);
		if (shaftProvider != null)
			return shaftProvider.findExit(level, inboundShaft);

		PortalTrackProvider trackProvider = PortalTrackProvider.REGISTRY.get(portalState);
		if (trackProvider == null)
			return null;

		PortalTrackProvider.Exit trackExit = trackProvider.findExit(level, inboundShaft);
		return trackExit == null ? null : new Exit(trackExit.level(), trackExit.face());
	}

	record Exit(ServerLevel level, BlockFace face) {}
}