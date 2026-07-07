package com.reggarf.mods.transferables.api;

import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Shaft version of Create's PortalTrackProvider.
 * It keeps shaft portal logic separate while still falling back to Create's
 * registered portal-track providers for vanilla Nether portals and compat portals.
 */
@FunctionalInterface
public interface PortalShaftProvider {
	SimpleRegistry<Block, PortalShaftProvider> REGISTRY = SimpleRegistry.create();

	@Nullable
	Exit findExit(ServerLevel level, BlockFace face);

	static boolean isSupportedPortal(BlockState state) {
		return REGISTRY.get(state) != null || PortalTrackProvider.isSupportedPortal(state);
	}

	@Nullable
	static Exit getOtherSide(ServerLevel level, BlockFace inboundShaft) {
		BlockPos portalPos = inboundShaft.getConnectedPos();
		BlockState portalState = level.getBlockState(portalPos);

		PortalShaftProvider shaftProvider = REGISTRY.get(portalState);
		if (shaftProvider != null)
			return shaftProvider.findExit(level, inboundShaft);

		PortalTrackProvider trackProvider = PortalTrackProvider.REGISTRY.get(portalState);
		if (trackProvider == null)
			return null;

		PortalTrackProvider.Exit trackExit = trackProvider.findExit(level, inboundShaft);
		return trackExit == null ? null : new Exit(trackExit.level(), trackExit.face());
	}

	@Nullable
	static Exit fromTeleporter(ServerLevel level, BlockFace face, ResourceKey<Level> firstDimension,
		ResourceKey<Level> secondDimension, Function<ServerLevel, ITeleporter> customPortalForcer) {
		PortalTrackProvider.Exit trackExit = PortalTrackProvider.fromTeleporter(level, face, firstDimension,
			secondDimension, customPortalForcer);
		return trackExit == null ? null : new Exit(trackExit.level(), trackExit.face());
	}

	@Nullable
	static Exit fromProbe(ServerLevel level, BlockFace face, ResourceKey<Level> firstDimension,
		ResourceKey<Level> secondDimension,
		BiFunction<ServerLevel, SuperGlueEntity, PortalInfo> portalInfoProvider) {
		PortalTrackProvider.Exit trackExit = PortalTrackProvider.fromProbe(level, face, firstDimension, secondDimension,
			portalInfoProvider);
		return trackExit == null ? null : new Exit(trackExit.level(), trackExit.face());
	}

	record Exit(ServerLevel level, BlockFace face) {}
}
