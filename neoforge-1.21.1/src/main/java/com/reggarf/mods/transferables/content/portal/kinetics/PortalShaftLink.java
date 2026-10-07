package com.reggarf.mods.transferables.content.portal.kinetics;

import javax.annotation.Nullable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class PortalShaftLink {
	private PortalShaftLink() {}

	@Nullable
	public static PortalProvider.Exit resolve(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		return PortalProvider.getOtherSide(level, new BlockFace(shaftPos, towardPortal));
	}

	public static boolean isShaftFacingPortal(Level level, BlockPos shaftPos, Direction towardPortal) {
		BlockState state = level.getBlockState(shaftPos);
		Direction.Axis axis = getPortalShaftAxis(state);
		if (axis == null || axis != towardPortal.getAxis())
			return false;
		return PortalProvider.isSupportedPortal(level.getBlockState(shaftPos.relative(towardPortal)));
	}

	public static boolean isPortalShaft(BlockState state) {
		Block block = state.getBlock();
		return block instanceof ShaftBlock
				|| block instanceof EncasedShaftBlock
				|| block instanceof CogWheelBlock
				|| block instanceof EncasedCogwheelBlock;
	}

	@Nullable
	public static Direction.Axis getPortalShaftAxis(BlockState state) {
		return isPortalShaft(state) && state.hasProperty(BlockStateProperties.AXIS)
				? state.getValue(BlockStateProperties.AXIS)
				: null;
	}
}
