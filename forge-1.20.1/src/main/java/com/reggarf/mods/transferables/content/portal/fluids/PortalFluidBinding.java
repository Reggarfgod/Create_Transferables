package com.reggarf.mods.transferables.content.portal.fluids;

import java.util.HashSet;
import java.util.Set;

import com.reggarf.mods.transferables.api.PortalBinding;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.content.portal.PortalDropEvents;
import com.reggarf.mods.transferables.content.portal.PortalPlacement;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Both portal endpoints use Mechanical Pumps.
 * The source-side pump pushes fluid through the portal, and the destination-side
 * pump receives fluid and outputs it into its connected fluid network.
 */
public final class PortalFluidBinding {
	private PortalFluidBinding() {}

	private static final int EXIT_PLACE_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
	private static final Set<GlobalPos> CASCADING_BREAKS = new HashSet<>();
	private static final String LANG = "transferables.portal_pump";

	public static void connectToPortal(FluidTransportBehaviour pump, ServerLevel level) {
		if (PortalPlacement.isSuppressed())
			return;
		if (!(pump instanceof PortalPumpAccess access))
			return;
		if (!access.transferables$isPlayerPlaced())
			return;

		BlockPos pos = pump.getPos();
		BlockState state = pump.blockEntity.getBlockState();
		if (!PortalFluidLink.isPortalPump(state))
			return;

		Direction.Axis axis = state.getValue(PumpBlock.FACING).getAxis();

		if (access.transferables$isPumpPortalConnected()) {
			Direction bound = access.transferables$getPumpPortalDirection();
			if (bound != null && PortalFluidLink.isFacingPortal(level, pos, bound)) {
				PortalBinding partner = access.transferables$getPumpBoundPartner();
				if (partner != null) {
					ServerLevel other = level.getServer().getLevel(partner.dimension());
					if (other != null && other.isLoaded(partner.pos())
							&& PortalFluidLink.isPortalPump(other.getBlockState(partner.pos())))
						return;
				}
			}
			disconnect(pump, access, level, false);
		}

		boolean foundPortal = false;
		String fail = null;
		BlockPos failPos = null;

		for (Direction towardPortal : Iterate.directionsInAxis(axis)) {
			if (!PortalFluidLink.isFacingPortal(level, pos, towardPortal))
				continue;

			foundPortal = true;
			PortalProvider.Exit exit = PortalProvider.getOtherSide(level, new BlockFace(pos, towardPortal));
			if (exit == null) {
				fail = "missing";
				continue;
			}

			BlockPos exitPos = exit.face().getPos();
			BlockState exitState = exit.level().getBlockState(exitPos);
			if (!exitState.canBeReplaced() && !PortalFluidLink.isPortalPump(exitState) && !PortalFluidLink.isPortalPipe(exitState)) {
				fail = "blocked";
				failPos = exitPos;
				continue;
			}

			boolean wasAutoPlaced = placeOrReuseExitPump(level, pos, exit);
			if (!wasAutoPlaced && !PortalFluidLink.isPortalPump(exit.level().getBlockState(exitPos))) {
				fail = "blocked";
				failPos = exitPos;
				continue;
			}

			FluidTransportBehaviour exitPump = BlockEntityBehaviour.get(exit.level(), exitPos,
					FluidTransportBehaviour.TYPE);
			if (exitPump == null || !(exitPump instanceof PortalPumpAccess exitAccess)) {
				fail = "blocked";
				failPos = exitPos;
				continue;
			}

			bindPair(level, pos, towardPortal, exit.level(), exitPos, exit.face().getFace(), access, exitAccess);
			pump.blockEntity.notifyUpdate();
			exitPump.blockEntity.notifyUpdate();
			FluidPropagator.propagateChangedPipe(level, pos, state);
			FluidPropagator.propagateChangedPipe(exit.level(), exitPos, exit.level().getBlockState(exitPos));
			level.updateNeighborsAt(pos, state.getBlock());
			exit.level().updateNeighborsAt(exitPos, exitPump.blockEntity.getBlockState().getBlock());
			return;
		}

		if (foundPortal && fail != null) {
			Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8.0, false);
			if (nearest != null) {
				Component msg = "blocked".equals(fail) && failPos != null
						? Component.translatable(LANG + ".blocked", failPos.getX(), failPos.getY(), failPos.getZ())
						: Component.translatable(LANG + ".missing");
				nearest.displayClientMessage(msg, true);
			}
		}
	}

	public static void onRemoved(FluidTransportBehaviour node, PortalPumpAccess access, ServerLevel level) {
		GlobalPos here = GlobalPos.of(level.dimension(), node.getPos());
		if (CASCADING_BREAKS.remove(here))
			disconnect(node, access, level, false);
		else
			disconnect(node, access, level, true);
	}

	public static void onPortalNeighborLost(FluidTransportBehaviour node, PortalPumpAccess access, ServerLevel level) {
		if (!access.transferables$isPumpPortalConnected())
			return;
		Direction towardPortal = access.transferables$getPumpPortalDirection();
		if (towardPortal != null && PortalFluidLink.isFacingPortal(level, node.getPos(), towardPortal))
			return;
		level.destroyBlock(node.getPos(), true);
	}

	private static void bindPair(ServerLevel localLevel, BlockPos localPos, Direction localTowardPortal,
	                             ServerLevel exitLevel, BlockPos exitPos, Direction exitTowardPortal,
	                             PortalPumpAccess localAccess, PortalPumpAccess exitAccess) {
		PortalBinding local = new PortalBinding(localLevel.dimension(), localPos);
		PortalBinding exit = new PortalBinding(exitLevel.dimension(), exitPos);
		localAccess.transferables$setPumpPortalConnection(localTowardPortal, exit);
		exitAccess.transferables$setPumpPortalConnection(exitTowardPortal, local);
		exitAccess.transferables$setPlayerPlaced(false);
	}

	/**
	 * Places or reuses a Mechanical Pump on the exit side of the portal.
	 * Returns true if a pump was newly placed or successfully configured.
	 */
	private static boolean placeOrReuseExitPump(ServerLevel sourceLevel, BlockPos sourcePos,
	                                            PortalProvider.Exit exit) {
		ServerLevel exitLevel = exit.level();
		BlockPos exitPos = exit.face().getPos();
		BlockState existing = exitLevel.getBlockState(exitPos);
		Direction exitTowardPortal = exit.face().getFace();
		Direction exitFacing = exitTowardPortal.getOpposite();
		PortalBinding expectedPartner = new PortalBinding(sourceLevel.dimension(), sourcePos);
		BlockState pumpState = AllBlocks.MECHANICAL_PUMP.getDefaultState()
				.setValue(PumpBlock.FACING, exitFacing);

		if (PortalFluidLink.isPortalPump(existing)) {
			FluidTransportBehaviour existingNode = BlockEntityBehaviour.get(exitLevel, exitPos,
					FluidTransportBehaviour.TYPE);
			if (!(existingNode instanceof PortalPumpAccess existingAccess))
				return false;

			if (existingAccess.transferables$isPumpPortalConnected()) {
				PortalBinding partner = existingAccess.transferables$getPumpBoundPartner();
				if (partner != null && !partner.equals(expectedPartner))
					return false;
			}

			boolean[] ok = { true };
			PortalPlacement.runSuppressed(() -> {
				if (existing.hasProperty(PumpBlock.FACING)
						&& existing.getValue(PumpBlock.FACING) != exitFacing) {
					ok[0] = exitLevel.setBlock(exitPos, pumpState, EXIT_PLACE_FLAGS);
				}
				FluidTransportBehaviour after = BlockEntityBehaviour.get(exitLevel, exitPos,
						FluidTransportBehaviour.TYPE);
				if (after instanceof PortalPumpAccess a) {
					a.transferables$setPlayerPlaced(false);
					after.blockEntity.notifyUpdate();
				}
			});
			return ok[0];
		}

		if (PortalFluidLink.isPortalPipe(existing) || existing.canBeReplaced()) {
			boolean[] ok = { false };
			PortalPlacement.runSuppressed(() -> {
				ok[0] = exitLevel.setBlock(exitPos, pumpState, EXIT_PLACE_FLAGS);
				FluidTransportBehaviour exitPump = BlockEntityBehaviour.get(exitLevel, exitPos,
						FluidTransportBehaviour.TYPE);
				if (ok[0] && exitPump instanceof PortalPumpAccess exitAccess) {
					exitAccess.transferables$setPlayerPlaced(false);
					exitPump.blockEntity.notifyUpdate();
				}
			});
			return ok[0];
		}

		return false;
	}

	private static void disconnect(FluidTransportBehaviour node, PortalPumpAccess access, ServerLevel level,
	                               boolean breakPartner) {
		PortalBinding partner = access.transferables$getPumpBoundPartner();
		BlockPos localPos = node.getPos();
		access.transferables$clearPumpPortalConnection();
		if (partner == null)
			return;

		PortalFluidBridges.clear(level.dimension(), localPos, partner.dimension(), partner.pos());

		ServerLevel otherLevel = level.getServer().getLevel(partner.dimension());
		if (otherLevel == null || !otherLevel.isLoaded(partner.pos()))
			return;

		FluidTransportBehaviour partnerNode = BlockEntityBehaviour.get(otherLevel, partner.pos(),
				FluidTransportBehaviour.TYPE);
		if (partnerNode instanceof PortalPumpAccess partnerAccess) {
			partnerAccess.transferables$clearPumpPortalConnection();
			partnerNode.blockEntity.notifyUpdate();
		}

		BlockState partnerState = otherLevel.getBlockState(partner.pos());
		if (!breakPartner)
			return;

		if (PortalFluidLink.isPortalPump(partnerState)) {
			CASCADING_BREAKS.add(GlobalPos.of(partner.dimension(), partner.pos()));
			PortalDropEvents.suppressNextDrop(partner.dimension(), partner.pos());
			PortalPlacement.runSuppressed(() -> otherLevel.destroyBlock(partner.pos(), false));
		}
	}
}
