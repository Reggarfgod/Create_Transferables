package com.reggarf.mods.transferables.network;

import javax.annotation.Nullable;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class PortalShaftLink {
	private PortalShaftLink() {}

	public record PortalShaftEndpoint(ServerLevel level, BlockPos shaftPos) {}

	@Nullable
	public static PortalTrackProvider.Exit resolve(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		BlockPos portalPos = shaftPos.relative(towardPortal);
		Block portalBlock = level.getBlockState(portalPos).getBlock();

		PortalTrackProvider provider = PortalTrackProvider.REGISTRY.get(portalBlock);
		if (provider == null)
			return null;

		BlockFace inbound = new BlockFace(shaftPos, towardPortal);
		return provider.findExit(level, inbound);
	}

	@Nullable
	public static PortalShaftEndpoint resolveShaft(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		if (!isShaftOnPortalFace(level, shaftPos, towardPortal))
			return null;

		PortalTrackProvider.Exit exit = resolve(level, shaftPos, towardPortal);
		if (exit == null)
			return null;

		ServerLevel otherLevel = exit.level();
		BlockPos otherShaftPos = exit.face().getPos();
		if (!otherLevel.isLoaded(otherShaftPos))
			return null;
		if (!isShaft(otherLevel, otherShaftPos))
			return null;
		if (!linksBack(otherLevel, otherShaftPos, level, shaftPos))
			return null;

		return new PortalShaftEndpoint(otherLevel, otherShaftPos);
	}

	private static boolean linksBack(ServerLevel level, BlockPos shaftPos, ServerLevel expectedLevel,
		BlockPos expectedShaftPos) {
		BlockState state = level.getBlockState(shaftPos);
		if (!(state.getBlock() instanceof ShaftBlock))
			return false;

		Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
		Direction[] ends = {
			Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
			Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)
		};

		for (Direction dir : ends) {
			if (!isShaftOnPortalFace(level, shaftPos, dir))
				continue;

			PortalTrackProvider.Exit back = resolve(level, shaftPos, dir);
			if (back == null)
				continue;
			if (back.level() == expectedLevel && back.face().getPos().equals(expectedShaftPos))
				return true;
		}

		return false;
	}

	private static boolean isShaftOnPortalFace(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		BlockState state = level.getBlockState(shaftPos);
		if (!(state.getBlock() instanceof ShaftBlock))
			return false;

		if (state.getValue(BlockStateProperties.AXIS) != towardPortal.getAxis())
			return false;

		Block portalBlock = level.getBlockState(shaftPos.relative(towardPortal)).getBlock();
		return PortalTrackProvider.REGISTRY.get(portalBlock) != null;
	}

	private static boolean isShaft(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockState(pos).getBlock() instanceof ShaftBlock))
			return false;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity instanceof KineticBlockEntity;
	}
}
