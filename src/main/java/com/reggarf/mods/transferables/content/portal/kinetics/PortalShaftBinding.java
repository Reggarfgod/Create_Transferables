package com.reggarf.mods.transferables.content.portal.kinetics;

import java.util.HashSet;
import java.util.Set;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.api.PortalBinding;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.content.portal.PortalDropEvents;
import com.reggarf.mods.transferables.content.portal.PortalMessages;
import com.reggarf.mods.transferables.content.portal.PortalPlacement;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Mirrors Create's {@code TrackBlock.connectToPortal}.
 * <p>
 * Create places the exit already marked as a portal shape so it never re-runs connect.
 * We do the same by: (1) suppressing connect during exit {@code setBlock}, and
 * (2) never letting auto-placed exits initiate a new link (only the player-placed side does).
 */
public final class PortalShaftBinding {
	private static final String LANG = "transferables.portal_shaft";
	private static final Set<GlobalPos> CASCADING_BREAKS = new HashSet<>();

	/** Like Create: place exit without neighbor-driven reconnect mid-setBlock. */
	private static final int EXIT_PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private PortalShaftBinding() {}

	public static void connectToPortal(KineticBlockEntity shaft, ServerLevel level) {
		if (PortalPlacement.isSuppressed())
			return;

		BlockState state = shaft.getBlockState();
		Direction.Axis axis = PortalShaftLink.getPortalShaftAxis(state);
		if (axis == null || !(shaft instanceof PortalAccess access))
			return;

		// Create: exit tracks are already portal-shaped and never call connectToPortal.
		// Auto-placed shafts must not initiate — only repair comes from the player-placed side.
		if (!access.transferables$isPlayerPlaced())
			return;

		BlockPos pos = shaft.getBlockPos();

		if (access.transferables$isPortalConnected()) {
			Direction bound = access.transferables$getPortalDirection();
			if (bound != null && PortalShaftLink.isShaftFacingPortal(level, pos, bound))
				return;
			disconnect(shaft, access, level, false);
		}

		boolean foundPortal = false;
		String fail = null;
		BlockPos failPos = null;

		for (Direction towardPortal : Iterate.directionsInAxis(axis)) {
			if (!PortalShaftLink.isShaftFacingPortal(level, pos, towardPortal))
				continue;

			foundPortal = true;
			PortalProvider.Exit exit = PortalShaftLink.resolve(level, pos, towardPortal);
			if (exit == null) {
				fail = "missing";
				continue;
			}

			BlockPos exitPos = exit.face().getPos();
			BlockState exitState = exit.level().getBlockState(exitPos);
			if (!exitState.canBeReplaced() && !PortalShaftLink.isPortalShaft(exitState)) {
				fail = "blocked";
				failPos = exitPos;
				continue;
			}

			Direction.Axis exitAxis = exit.face().getFace().getAxis();
			if (!placeOrReuseExit(level, pos, state, exit, exitAxis)) {
				fail = "blocked";
				failPos = exitPos;
				continue;
			}

			if (!(exit.level().getBlockEntity(exitPos) instanceof KineticBlockEntity exitShaft)
					|| !(exitShaft instanceof PortalAccess exitAccess)) {
				fail = "blocked";
				failPos = exitPos;
				continue;
			}

			bindPair(level, pos, towardPortal, exit.level(), exitPos, exit.face().getFace(), access, exitAccess);
			shaft.notifyUpdate();
			exitShaft.notifyUpdate();

			// Neighbors after bind — Create updates both sides only once linking is done
			level.updateNeighborsAt(pos, state.getBlock());
			exit.level().updateNeighborsAt(exitPos, exitShaft.getBlockState().getBlock());
			return;
		}

		if (!foundPortal)
			return;

		PortalMessages.failAndBreak(level, pos, LANG, fail != null ? fail : "missing", failPos);
	}

	public static void onRemoved(KineticBlockEntity shaft, PortalAccess access, ServerLevel level) {
		GlobalPos here = GlobalPos.of(level.dimension(), shaft.getBlockPos());
		if (CASCADING_BREAKS.remove(here))
			disconnect(shaft, access, level, false);
		else
			disconnect(shaft, access, level, true);
	}

	public static void onPortalNeighborLost(KineticBlockEntity shaft, PortalAccess access, ServerLevel level) {
		if (!access.transferables$isPortalConnected())
			return;
		Direction towardPortal = access.transferables$getPortalDirection();
		if (towardPortal != null && PortalShaftLink.isShaftFacingPortal(level, shaft.getBlockPos(), towardPortal))
			return;
		level.destroyBlock(shaft.getBlockPos(), true);
	}

	private static void bindPair(ServerLevel localLevel, BlockPos localPos, Direction localTowardPortal,
	                             ServerLevel exitLevel, BlockPos exitPos, Direction exitTowardPortal,
	                             PortalAccess localAccess, PortalAccess exitAccess) {
		PortalBinding local = new PortalBinding(localLevel.dimension(), localPos);
		PortalBinding exit = new PortalBinding(exitLevel.dimension(), exitPos);
		localAccess.transferables$setPortalConnection(localTowardPortal, exit);
		exitAccess.transferables$setPortalConnection(exitTowardPortal, local);
		exitAccess.transferables$setPlayerPlaced(false);
	}

	/**
	 * Place a single exit (or reuse one already linked to us). Never steal another portal link.
	 */
	private static boolean placeOrReuseExit(ServerLevel sourceLevel, BlockPos sourcePos, BlockState sourceState,
	                                        PortalProvider.Exit exit, Direction.Axis exitAxis) {
		ServerLevel exitLevel = exit.level();
		BlockPos exitPos = exit.face().getPos();
		BlockState existing = exitLevel.getBlockState(exitPos);
		PortalBinding expectedPartner = new PortalBinding(sourceLevel.dimension(), sourcePos);

		if (PortalShaftLink.isPortalShaft(existing)) {
			if (!(exitLevel.getBlockEntity(exitPos) instanceof PortalAccess existingAccess))
				return false;

			if (existingAccess.transferables$isPortalConnected()) {
				PortalBinding partner = existingAccess.transferables$getBoundPartner();
				if (partner != null && !partner.equals(expectedPartner))
					return false; // owned by a different link
			}

			boolean[] ok = { true };
			PortalPlacement.runSuppressed(() -> {
				if (existing.hasProperty(BlockStateProperties.AXIS)
						&& existing.getValue(BlockStateProperties.AXIS) != exitAxis) {
					ok[0] = exitLevel.setBlock(exitPos, existing.setValue(BlockStateProperties.AXIS, exitAxis),
							EXIT_PLACE_FLAGS);
				}
				existingAccess.transferables$setPlayerPlaced(false);
			});
			return ok[0];
		}

		if (!existing.canBeReplaced())
			return false;

		BlockState toPlace = sourceState.getBlock().defaultBlockState();
		if (toPlace.hasProperty(BlockStateProperties.AXIS))
			toPlace = toPlace.setValue(BlockStateProperties.AXIS, exitAxis);
		final BlockState placed = toPlace;

		boolean[] ok = { false };
		PortalPlacement.runSuppressed(() -> {
			ok[0] = exitLevel.setBlock(exitPos, placed, EXIT_PLACE_FLAGS);
			if (ok[0] && exitLevel.getBlockEntity(exitPos) instanceof PortalAccess exitAccess)
				exitAccess.transferables$setPlayerPlaced(false);
		});
		return ok[0];
	}

	private static void disconnect(KineticBlockEntity shaft, PortalAccess access, ServerLevel level,
	                               boolean breakPartner) {
		PortalBinding partner = access.transferables$getBoundPartner();
		access.transferables$clearPortalConnection();

		if (partner == null)
			return;

		ServerLevel otherLevel = level.getServer().getLevel(partner.dimension());
		if (otherLevel == null || !otherLevel.isLoaded(partner.pos()))
			return;

		if (otherLevel.getBlockEntity(partner.pos()) instanceof KineticBlockEntity partnerShaft
				&& partnerShaft instanceof PortalAccess partnerAccess) {
			partnerAccess.transferables$clearPortalConnection();
			partnerShaft.notifyUpdate();

			if (breakPartner && PortalShaftLink.isPortalShaft(otherLevel.getBlockState(partner.pos()))) {
				CASCADING_BREAKS.add(GlobalPos.of(partner.dimension(), partner.pos()));
				PortalDropEvents.suppressNextDrop(partner.dimension(), partner.pos());
				PortalPlacement.runSuppressed(() -> otherLevel.destroyBlock(partner.pos(), true));
			}
		}
	}
}
