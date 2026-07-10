package com.reggarf.mods.transferables.network;

import javax.annotation.Nullable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Fluid-pipe counterpart to PortalShaftLink: finds the pipe on the far side of a portal. */
public final class PortalFluidLink {
	private PortalFluidLink() {}

	public record PipeEndpoint(ResourceKey<Level> dim, BlockPos pos) {}

	@Nullable
	public static PipeEndpoint resolvePipe(ServerLevel level, BlockPos pipePos, Direction towardPortal) {
		if (!isPipeOnPortalFace(level, pipePos, towardPortal))
			return null;

		PortalProvider.Exit exit =
			PortalProvider.getOtherSide(level, new BlockFace(pipePos, towardPortal));
		if (exit == null)
			return null;

		ServerLevel otherLevel = exit.level();
		BlockPos otherPos = exit.face()
			.getPos();
		if (!otherLevel.isLoaded(otherPos))
			return null;
		if (!isLinkedPipe(otherLevel, otherPos))
			return null;
		if (!linksBack(otherLevel, otherPos, level, pipePos))
			return null;

		return new PipeEndpoint(otherLevel.dimension(), otherPos);
	}

	/** True if there's any Create fluid pipe here (used to validate the partner is still a pipe). */
	public static boolean isLinkedPipe(ServerLevel level, BlockPos pos) {
		return BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE) != null;
	}

	private static boolean isPipeOnPortalFace(ServerLevel level, BlockPos pos, Direction towardPortal) {
		FluidTransportBehaviour pipe = BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE);
		if (pipe == null)
			return false;
		if (!pipe.canHaveFlowToward(level.getBlockState(pos), towardPortal))
			return false; // face isn't an open pipe end (true for straight/glass pipes along their axis)
		return PortalProvider.isSupportedPortal(level.getBlockState(pos.relative(towardPortal)));
	}

	private static boolean linksBack(ServerLevel level, BlockPos pipePos, ServerLevel expectedLevel,
		BlockPos expectedPos) {
		for (Direction dir : Iterate.directions) {
			if (!isPipeOnPortalFace(level, pipePos, dir))
				continue;
			PortalProvider.Exit back = PortalProvider.getOtherSide(level, new BlockFace(pipePos, dir));
			if (back == null)
				continue;
			if (back.level()
				.dimension()
				.equals(expectedLevel.dimension())
				&& back.face()
					.getPos()
					.equals(expectedPos))
				return true;
		}
		return false;
	}
}