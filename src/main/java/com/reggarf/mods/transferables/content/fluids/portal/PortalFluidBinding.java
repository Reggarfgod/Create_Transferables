package com.reggarf.mods.transferables.content.fluids.portal;

import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.content.kinetics.portal.PortalShaftBinding;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Predicates;

/**
 * Mirrors {@link PortalShaftBinding}: portal-facing mechanical pumps auto-place an exit pump,
 * bind both ends, and share fluid through {@link PortalFluidBridges}.
 */
public final class PortalFluidBinding {
	private static final Set<GlobalPos> CASCADING_BREAKS = new HashSet<>();

	private PortalFluidBinding() {}

	public static void connectToPortal(FluidTransportBehaviour pump, ServerLevel level) {
		if (!(pump instanceof PortalPumpAccess access))
			return;

		BlockPos pos = pump.getPos();
		BlockState state = pump.blockEntity.getBlockState();
		if (!PortalFluidLink.isPortalPump(state))
			return;

		Direction.Axis axis = state.getValue(PumpBlock.FACING).getAxis();

		if (access.transferables$isPumpPortalConnected()) {
			Direction bound = access.transferables$getPumpPortalDirection();
			if (bound != null && PortalFluidLink.isPumpFacingPortal(level, pos, bound))
				return;
			disconnect(pump, access, level, false);
		}

		for (Direction towardPortal : Iterate.directionsInAxis(axis)) {
			if (!PortalFluidLink.isPumpFacingPortal(level, pos, towardPortal))
				continue;

			PortalProvider.Exit exit = PortalProvider.getOtherSide(level, new BlockFace(pos, towardPortal));
			if (exit == null) {
				failPlacement(level, pos, "missing", null);
				return;
			}

			BlockPos exitPos = exit.face().getPos();
			BlockState exitState = exit.level().getBlockState(exitPos);
			if (!exitState.canBeReplaced() && !PortalFluidLink.isPortalPump(exitState)) {
				failPlacement(level, pos, "blocked", exitPos);
				return;
			}

			placeExitPumpIfNeeded(pump, exit);

			FluidTransportBehaviour exitPump = BlockEntityBehaviour.get(exit.level(), exitPos,
					FluidTransportBehaviour.TYPE);
			if (exitPump == null || !(exitPump instanceof PortalPumpAccess exitAccess)) {
				failPlacement(level, pos, "blocked", exitPos);
				return;
			}

			bindPair(level, pos, towardPortal, exit.level(), exitPos, exit.face().getFace(), access, exitAccess);
			pump.blockEntity.sendData();
			exitPump.blockEntity.sendData();
			FluidPropagator.propagateChangedPipe(level, pos, state);
			FluidPropagator.propagateChangedPipe(exit.level(), exitPos, exit.level().getBlockState(exitPos));
			return;
		}
	}

	public static void onRemoved(FluidTransportBehaviour pump, PortalPumpAccess access, ServerLevel level) {
		GlobalPos here = GlobalPos.of(level.dimension(), pump.getPos());
		if (CASCADING_BREAKS.remove(here))
			disconnect(pump, access, level, false);
		else
			disconnect(pump, access, level, true);
	}

	public static void onPortalNeighborLost(FluidTransportBehaviour pump, PortalPumpAccess access, ServerLevel level) {
		if (!access.transferables$isPumpPortalConnected())
			return;
		Direction towardPortal = access.transferables$getPumpPortalDirection();
		if (towardPortal != null && PortalFluidLink.isPumpFacingPortal(level, pump.getPos(), towardPortal))
			return;
		level.destroyBlock(pump.getPos(), true);
	}

	private static void bindPair(ServerLevel localLevel, BlockPos localPos, Direction localTowardPortal,
	                             ServerLevel exitLevel, BlockPos exitPos, Direction exitTowardPortal,
	                             PortalPumpAccess localAccess, PortalPumpAccess exitAccess) {
		PortalShaftBinding.Binding local = new PortalShaftBinding.Binding(localLevel.dimension(), localPos);
		PortalShaftBinding.Binding exit = new PortalShaftBinding.Binding(exitLevel.dimension(), exitPos);

		localAccess.transferables$setPumpPortalConnection(localTowardPortal, exit);
		localAccess.transferables$setPumpPortalBinding(exit);

		exitAccess.transferables$setPumpPortalConnection(exitTowardPortal, local);
		exitAccess.transferables$setPumpPortalBinding(local);
	}

	private static void placeExitPumpIfNeeded(FluidTransportBehaviour source, PortalProvider.Exit exit) {
		ServerLevel exitLevel = exit.level();
		BlockPos exitPos = exit.face().getPos();
		BlockState existing = exitLevel.getBlockState(exitPos);

		if (PortalFluidLink.isPortalPump(existing))
			return;
		if (!existing.canBeReplaced())
			return;

		BlockState sourceState = source.blockEntity.getBlockState();
		BlockState placed = sourceState.getBlock().defaultBlockState()
				.setValue(PumpBlock.FACING, sourceState.getValue(PumpBlock.FACING));
		exitLevel.setBlock(exitPos, placed, 3);
	}

	private static void disconnect(FluidTransportBehaviour pump, PortalPumpAccess access, ServerLevel level,
	                               boolean breakPartner) {
		PortalShaftBinding.Binding partner = access.transferables$getPumpBoundPartner();
		access.transferables$clearPumpPortalConnection();

		if (partner == null)
			return;

		ServerLevel otherLevel = level.getServer().getLevel(partner.dimension());
		if (otherLevel == null || !otherLevel.isLoaded(partner.pos()))
			return;

		FluidTransportBehaviour partnerPump = BlockEntityBehaviour.get(otherLevel, partner.pos(),
				FluidTransportBehaviour.TYPE);
		if (partnerPump instanceof PortalPumpAccess partnerAccess) {
			partnerAccess.transferables$clearPumpPortalConnection();
			partnerAccess.transferables$setPumpPortalBinding(null);
			partnerPump.blockEntity.sendData();

			if (breakPartner && PortalFluidLink.isPortalPump(otherLevel.getBlockState(partner.pos()))) {
				CASCADING_BREAKS.add(GlobalPos.of(partner.dimension(), partner.pos()));
				otherLevel.destroyBlock(partner.pos(), true);
			}
		}
	}

	private static void failPlacement(ServerLevel level, BlockPos pos, String reason, @Nullable BlockPos failPos) {
		notifyFailure(level, pos, reason, failPos);
		level.destroyBlock(pos, true);
	}

	private static void notifyFailure(ServerLevel level, BlockPos pos, String reason, @Nullable BlockPos failPos) {
		Player player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, Predicates.alwaysTrue());
		if (!(player instanceof ServerPlayer serverPlayer))
			return;

		serverPlayer.displayClientMessage(Component.literal(" ")
				.append(Component.translatable("transferables.portal_pump.failed").withStyle(ChatFormatting.GOLD)),
				false);
		MutableComponent detail = failPos != null
				? Component.translatable("transferables.portal_pump." + reason, failPos.getX(), failPos.getY(),
						failPos.getZ())
				: Component.translatable("transferables.portal_pump." + reason);
		serverPlayer.displayClientMessage(Component.literal(" - ").withStyle(ChatFormatting.GRAY).append(detail), false);
	}
}
