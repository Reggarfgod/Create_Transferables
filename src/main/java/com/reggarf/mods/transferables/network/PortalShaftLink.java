package com.reggarf.mods.transferables.network;

import com.reggarf.mods.transferables.api.PortalShaftProvider;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;

public final class PortalShaftLink {
	private PortalShaftLink() {}

	public record PortalShaftEndpoint(ServerLevel level, BlockPos shaftPos) {}

	@Nullable
	public static PortalShaftProvider.Exit resolve(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		BlockFace inbound = new BlockFace(shaftPos, towardPortal);
		return PortalShaftProvider.getOtherSide(level, inbound);
	}

	@Nullable
	public static PortalShaftEndpoint resolveShaft(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		if (!isShaftOnPortalFace(level, shaftPos, towardPortal))
			return null;

		PortalShaftProvider.Exit exit = resolve(level, shaftPos, towardPortal);
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


	public static boolean isPortalShaft(BlockState state) {
		Block block = state.getBlock();
		return block instanceof ShaftBlock || block instanceof EncasedShaftBlock;
	}

	@Nullable
	public static Direction.Axis getPortalShaftAxis(BlockState state) {
		return isPortalShaft(state) && state.hasProperty(BlockStateProperties.AXIS)
			? state.getValue(BlockStateProperties.AXIS)
			: null;
	}

	private static boolean linksBack(ServerLevel level, BlockPos shaftPos, ServerLevel expectedLevel,
		BlockPos expectedShaftPos) {
		BlockState state = level.getBlockState(shaftPos);
		Direction.Axis axis = getPortalShaftAxis(state);
		if (axis == null)
			return false;

		Direction[] ends = {
			Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
			Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)
		};

		for (Direction dir : ends) {
			if (!isShaftOnPortalFace(level, shaftPos, dir))
				continue;

			PortalShaftProvider.Exit back = resolve(level, shaftPos, dir);
			if (back == null)
				continue;
			if (back.level().dimension().equals(expectedLevel.dimension()) && back.face().getPos().equals(expectedShaftPos))
				return true;
		}

		return false;
	}

	private static boolean isShaftOnPortalFace(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		BlockState state = level.getBlockState(shaftPos);
		Direction.Axis axis = getPortalShaftAxis(state);
		if (axis == null || axis != towardPortal.getAxis())
			return false;

		return PortalShaftProvider.isSupportedPortal(level.getBlockState(shaftPos.relative(towardPortal)));
	}

	private static boolean isShaft(ServerLevel level, BlockPos pos) {
		if (!isPortalShaft(level.getBlockState(pos)))
			return false;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity instanceof KineticBlockEntity;
	}

}
