package com.reggarf.mods.transferables.api;

import javax.annotation.Nullable;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.track.AllPortalTracks;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;

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
		if (state.is(Blocks.NETHER_PORTAL) || state.getBlock() instanceof Portal)
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

		if (portalState.is(Blocks.NETHER_PORTAL) || portalState.getBlock() instanceof Portal) {
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
	 * Same exit placement as Create portal tracks ({@link AllPortalTracks#fromPortal}),
	 * with a resilient fallback when portal blockstates omit {@code HORIZONTAL_AXIS}.
	 */
	@Nullable
	static Exit fromNetherPortal(ServerLevel level, BlockFace inboundShaft) {
		BlockPos portalPos = inboundShaft.getConnectedPos();
		BlockState portalState = level.getBlockState(portalPos);
		if (!(portalState.getBlock() instanceof Portal portal))
			return null;

		if (portalState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
			try {
				PortalTrackProvider.Exit trackExit = AllPortalTracks.fromPortal(
						level, inboundShaft, Level.OVERWORLD, Level.NETHER, portal);
				if (trackExit != null)
					return new Exit(trackExit.level(), trackExit.face());
			} catch (Throwable ignored) {}
		}

		MinecraftServer server = level.getServer();
		ResourceKey<Level> targetDim = level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
		ServerLevel otherLevel = server.getLevel(targetDim);
		if (otherLevel == null)
			return null;

		SuperGlueEntity probe = new SuperGlueEntity(level, new AABB(portalPos));
		probe.setYRot(inboundShaft.getFace().toYRot());

		DimensionTransition transition;
		try {
			transition = portal.getPortalDestination(level, probe, probe.blockPosition());
		} catch (Throwable t) {
			return null;
		}
		if (transition == null || !server.isLevelEnabled(transition.newLevel()))
			return null;

		ServerLevel destLevel = transition.newLevel();
		BlockPos otherPortalPos = BlockPos.containing(transition.pos());
		BlockState otherPortalState = destLevel.getBlockState(otherPortalPos);
		if (!otherPortalState.is(portalState.getBlock()) && !isSupportedPortal(otherPortalState))
			return null;

		Direction targetDirection = inboundShaft.getFace();
		Direction.Axis otherAxis = getPortalAxis(otherPortalState);
		if (otherAxis != null && targetDirection.getAxis() == otherAxis)
			targetDirection = targetDirection.getClockWise();

		BlockPos otherPos = otherPortalPos.relative(targetDirection);
		return new Exit(destLevel, new BlockFace(otherPos, targetDirection.getOpposite()));
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
