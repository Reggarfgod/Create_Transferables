package com.reggarf.mods.transferables.content.portal.fluids;

import javax.annotation.Nullable;

import com.reggarf.mods.transferables.api.PortalBinding;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
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

/**
 * One player-placed mechanical pump bridges through a portal to an auto-placed fluid pipe
 * on the other side — only one pump is required.
 */
public final class PortalFluidLink {
	private PortalFluidLink() {}

	public record FluidEndpoint(ResourceKey<Level> dim, BlockPos pos) {}

	/** @deprecated use {@link #resolvePartner} */
	@Deprecated
	public record PumpEndpoint(ResourceKey<Level> dim, BlockPos pos) {}

	@Nullable
	public static FluidEndpoint resolvePartner(ServerLevel level, BlockPos selfPos, Direction towardPortal) {
		if (!isFacingPortal(level, selfPos, towardPortal))
			return null;

		FluidTransportBehaviour self = BlockEntityBehaviour.get(level, selfPos, FluidTransportBehaviour.TYPE);
		if (self instanceof PortalPumpAccess access && access.transferables$isPumpPortalConnected()) {
			PortalBinding bound = access.transferables$getPumpBoundPartner();
			if (bound != null) {
				ServerLevel other = level.getServer().getLevel(bound.dimension());
				if (other != null && other.isLoaded(bound.pos()) && isPortalFluidNode(other.getBlockState(bound.pos())))
					return new FluidEndpoint(bound.dimension(), bound.pos());
			}
		}

		PortalProvider.Exit exit = PortalProvider.getOtherSide(level, new BlockFace(selfPos, towardPortal));
		if (exit == null)
			return null;

		ServerLevel otherLevel = exit.level();
		BlockPos otherPos = exit.face().getPos();
		if (!otherLevel.isLoaded(otherPos))
			return null;
		if (!isPortalFluidNode(otherLevel.getBlockState(otherPos)))
			return null;
		if (!linksBack(otherLevel, otherPos, level, selfPos))
			return null;

		return new FluidEndpoint(otherLevel.dimension(), otherPos);
	}

	@Nullable
	@Deprecated
	public static PumpEndpoint resolvePump(ServerLevel level, BlockPos pumpPos, Direction towardPortal) {
		FluidEndpoint ep = resolvePartner(level, pumpPos, towardPortal);
		return ep == null ? null : new PumpEndpoint(ep.dim(), ep.pos());
	}

	public static boolean isFacingPortal(Level level, BlockPos pos, Direction towardPortal) {
		BlockState state = level.getBlockState(pos);
		if (!isPortalFluidNode(state))
			return false;
		FluidTransportBehaviour pipe = BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE);
		if (pipe == null)
			return false;
		if (!pipe.canHaveFlowToward(state, towardPortal))
			return false;
		return PortalProvider.isSupportedPortal(level.getBlockState(pos.relative(towardPortal)));
	}

	/** @deprecated use {@link #isFacingPortal} */
	@Deprecated
	public static boolean isPumpFacingPortal(Level level, BlockPos pumpPos, Direction towardPortal) {
		return isFacingPortal(level, pumpPos, towardPortal);
	}

	public static boolean isPortalPump(BlockState state) {
		return PumpBlock.isPump(state);
	}

	public static boolean isPortalPipe(BlockState state) {
		return FluidPipeBlock.isPipe(state) || state.getBlock() instanceof GlassFluidPipeBlock;
	}

	/** Pump (player side) or pipe (exit side) that can participate in a portal fluid link. */
	public static boolean isPortalFluidNode(BlockState state) {
		return isPortalPump(state) || isPortalPipe(state);
	}

	public static boolean isLinkedFluidNode(Level level, BlockPos pos) {
		return isPortalFluidNode(level.getBlockState(pos))
				&& BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE) != null;
	}

	private static boolean linksBack(ServerLevel level, BlockPos pos, ServerLevel expectedLevel,
	                                 BlockPos expectedPos) {
		FluidTransportBehaviour pipe = BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE);
		if (pipe instanceof PortalPumpAccess access && access.transferables$isPumpPortalConnected()) {
			PortalBinding partner = access.transferables$getPumpBoundPartner();
			if (partner != null
					&& partner.dimension().equals(expectedLevel.dimension())
					&& partner.pos().equals(expectedPos))
				return true;
		}

		for (Direction dir : Iterate.directions) {
			if (!isFacingPortal(level, pos, dir))
				continue;
			PortalProvider.Exit back = PortalProvider.getOtherSide(level, new BlockFace(pos, dir));
			if (back == null)
				continue;
			if (back.level().dimension().equals(expectedLevel.dimension())
					&& back.face().getPos().equals(expectedPos))
				return true;
		}
		return false;
	}
}
