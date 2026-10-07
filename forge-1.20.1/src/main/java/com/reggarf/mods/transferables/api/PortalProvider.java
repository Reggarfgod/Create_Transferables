package com.reggarf.mods.transferables.api;

import javax.annotation.Nullable;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.trains.track.AllPortalTracks;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Shaft/pump counterpart to Create's {@link PortalTrackProvider}.
 * Exit positions use the same math as portal tracks whenever possible.
 */
@FunctionalInterface
public interface PortalProvider {
	SimpleRegistry<Block, PortalProvider> REGISTRY = SimpleRegistry.create();

	@Nullable
	Exit findExit(ServerLevel level, BlockFace face);

	static void registerDefaults() {
		REGISTRY.register(Blocks.NETHER_PORTAL, PortalProvider::fromNetherPortal);
	}

	static boolean isSupportedPortal(BlockState state) {
		if (state == null)
			return false;
		if (state.is(Blocks.NETHER_PORTAL))
			return true;
		try {
			return REGISTRY.get(state) != null || PortalTrackProvider.isSupportedPortal(state);
		} catch (Throwable t) {
			return false;
		}
	}

	@Nullable
	static Exit getOtherSide(ServerLevel level, BlockFace inbound) {
		BlockPos portalPos = inbound.getConnectedPos();
		BlockState portalState = level.getBlockState(portalPos);

		PortalProvider custom = REGISTRY.get(portalState);
		if (custom != null) {
			try {
				Exit exit = custom.findExit(level, inbound);
				if (exit != null)
					return exit;
			} catch (Throwable ignored) {}
		}

		if (portalState.is(Blocks.NETHER_PORTAL)) {
			Exit exit = fromNetherPortal(level, inbound);
			if (exit != null)
				return exit;
		}

		try {
			PortalTrackProvider trackProvider = PortalTrackProvider.REGISTRY.get(portalState);
			if (trackProvider != null) {
				PortalTrackProvider.Exit trackExit = trackProvider.findExit(level, inbound);
				if (trackExit != null)
					return new Exit(trackExit.level(), trackExit.face());
			}
		} catch (Throwable ignored) {}

		return null;
	}

	/**
	 * Same exit placement as Create portal tracks ({@link AllPortalTracks#fromTeleporter}).
	 */
	@Nullable
	static Exit fromNetherPortal(ServerLevel level, BlockFace inboundShaft) {
		BlockPos portalPos = inboundShaft.getConnectedPos();
		BlockState portalState = level.getBlockState(portalPos);
		if (!portalState.is(Blocks.NETHER_PORTAL))
			return null;

		try {
			PortalTrackProvider.Exit trackExit = AllPortalTracks.fromTeleporter(
					level, inboundShaft, Level.OVERWORLD, Level.NETHER, ServerLevel::getPortalForcer);
			if (trackExit != null)
				return new Exit(trackExit.level(), trackExit.face());
		} catch (Throwable ignored) {}

		return null;
	}

	@Nullable
	static Direction.Axis getPortalAxis(BlockState state) {
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
			try {
				return state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
			} catch (IllegalArgumentException ignored) {}
		}
		if (state.hasProperty(BlockStateProperties.AXIS)) {
			try {
				return state.getValue(BlockStateProperties.AXIS);
			} catch (IllegalArgumentException ignored) {}
		}
		for (Property<?> prop : state.getProperties()) {
			if (prop.getValueClass() == Direction.Axis.class) {
				try {
					@SuppressWarnings("unchecked")
					Property<Direction.Axis> axisProp = (Property<Direction.Axis>) prop;
					return state.getValue(axisProp);
				} catch (IllegalArgumentException ignored) {}
			}
		}
		return null;
	}

	record Exit(ServerLevel level, BlockFace face) {}
}
