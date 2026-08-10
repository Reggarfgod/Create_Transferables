package com.reggarf.mods.transferables.network;

import javax.annotation.Nullable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Mechanical-pump counterpart to {@link PortalShaftLink}. */
public final class PortalFluidLink {
	private PortalFluidLink() {}

	public record PumpEndpoint(ResourceKey<Level> dim, BlockPos pos) {}

	@Nullable
	public static PumpEndpoint resolvePump(ServerLevel level, BlockPos pumpPos, Direction towardPortal) {
		if (!isPumpOnPortalFace(level, pumpPos, towardPortal))
			return null;

		PortalProvider.Exit exit = PortalProvider.getOtherSide(level, new BlockFace(pumpPos, towardPortal));
		if (exit == null)
			return null;

		ServerLevel otherLevel = exit.level();
		BlockPos otherPos = exit.face().getPos();
		if (!otherLevel.isLoaded(otherPos))
			return null;
		if (!isLinkedPump(otherLevel, otherPos))
			return null;
		if (!linksBack(otherLevel, otherPos, level, pumpPos))
			return null;

		return new PumpEndpoint(otherLevel.dimension(), otherPos);
	}

	public static boolean isPumpFacingPortal(ServerLevel level, BlockPos pumpPos, Direction towardPortal) {
		return isPumpOnPortalFace(level, pumpPos, towardPortal);
	}

	public static boolean isPortalPump(BlockState state) {
		return PumpBlock.isPump(state);
	}

	public static boolean isLinkedPump(ServerLevel level, BlockPos pos) {
		return isPortalPump(level.getBlockState(pos))
				&& BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE) != null;
	}

	private static boolean isPumpOnPortalFace(ServerLevel level, BlockPos pos, Direction towardPortal) {
		if (!isPortalPump(level.getBlockState(pos)))
			return false;
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE);
		if (pump == null)
			return false;
		if (!pump.canHaveFlowToward(level.getBlockState(pos), towardPortal))
			return false;
		return PortalProvider.isSupportedPortal(level.getBlockState(pos.relative(towardPortal)));
	}

	private static boolean linksBack(ServerLevel level, BlockPos pumpPos, ServerLevel expectedLevel,
	                                   BlockPos expectedPos) {
		for (Direction dir : Iterate.directions) {
			if (!isPumpOnPortalFace(level, pumpPos, dir))
				continue;
			PortalProvider.Exit back = PortalProvider.getOtherSide(level, new BlockFace(pumpPos, dir));
			if (back == null)
				continue;
			if (back.level().dimension().equals(expectedLevel.dimension())
					&& back.face().getPos().equals(expectedPos))
				return true;
		}
		return false;
	}
}
