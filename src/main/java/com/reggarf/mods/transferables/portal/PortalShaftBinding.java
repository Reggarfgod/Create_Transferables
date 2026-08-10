package com.reggarf.mods.transferables.portal;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.network.PortalShaftLink;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Predicates;

/**
 * Mirrors {@code TrackBlock.connectToPortal}: when a shaft is placed facing a portal it
 * auto-places the exit shaft, binds both ends, and links kinetic networks across dimensions.
 */
public final class PortalShaftBinding {
	private static final Set<GlobalPos> CASCADING_BREAKS = new HashSet<>();

	private PortalShaftBinding() {}

	public record Binding(ResourceKey<Level> dimension, BlockPos pos) {}

	/** Called after placement or when neighbors change — same role as {@code TrackBlock.tick}. */
	public static void connectToPortal(KineticBlockEntity shaft, ServerLevel level) {
		BlockState state = shaft.getBlockState();
		Direction.Axis axis = PortalShaftLink.getPortalShaftAxis(state);
		if (axis == null || !(shaft instanceof PortalAccess access))
			return;

		BlockPos pos = shaft.getBlockPos();

		if (access.transferables$isPortalConnected()) {
			Direction bound = access.transferables$getPortalDirection();
			if (bound != null && PortalShaftLink.isShaftFacingPortal(level, pos, bound))
				return;
			disconnect(shaft, access, level, false);
		}

		for (Direction towardPortal : Iterate.directionsInAxis(axis)) {
			if (!PortalShaftLink.isShaftFacingPortal(level, pos, towardPortal))
				continue;

			PortalProvider.Exit exit = PortalShaftLink.resolve(level, pos, towardPortal);
			if (exit == null) {
				failPlacement(level, pos, "missing", null);
				return;
			}

			BlockPos exitPos = exit.face().getPos();
			BlockState exitState = exit.level().getBlockState(exitPos);
			if (!exitState.canBeReplaced() && !PortalShaftLink.isPortalShaft(exitState)) {
				failPlacement(level, pos, "blocked", exitPos);
				return;
			}

			placeExitShaftIfNeeded(shaft, exit, state);

			if (!(exit.level().getBlockEntity(exitPos) instanceof KineticBlockEntity exitShaft)
					|| !(exitShaft instanceof PortalAccess exitAccess)) {
				failPlacement(level, pos, "blocked", exitPos);
				return;
			}

			bindPair(level, pos, towardPortal, exit.level(), exitPos, exit.face().getFace(), access, exitAccess);
			shaft.sendData();
			exitShaft.sendData();
			return;
		}
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
		Binding local = new Binding(localLevel.dimension(), localPos);
		Binding exit = new Binding(exitLevel.dimension(), exitPos);

		localAccess.transferables$setPortalConnection(localTowardPortal, exit);
		localAccess.transferables$setPortalBinding(exit);

		exitAccess.transferables$setPortalConnection(exitTowardPortal, local);
		exitAccess.transferables$setPortalBinding(local);
	}

	private static void placeExitShaftIfNeeded(KineticBlockEntity source, PortalProvider.Exit exit,
	                                           BlockState sourceState) {
		ServerLevel exitLevel = exit.level();
		BlockPos exitPos = exit.face().getPos();
		BlockState existing = exitLevel.getBlockState(exitPos);

		if (PortalShaftLink.isPortalShaft(existing))
			return;
		if (!existing.canBeReplaced())
			return;

		Direction.Axis axis = PortalShaftLink.getPortalShaftAxis(sourceState);
		if (axis == null)
			return;

		BlockState placed = sourceState.getBlock().defaultBlockState();
		if (placed.hasProperty(BlockStateProperties.AXIS))
			placed = placed.setValue(BlockStateProperties.AXIS, axis);
		exitLevel.setBlock(exitPos, placed, 3);
	}

	private static void disconnect(KineticBlockEntity shaft, PortalAccess access, ServerLevel level,
	                               boolean breakPartner) {
		Binding partner = access.transferables$getBoundPartner();
		access.transferables$clearPortalConnection();

		if (partner == null)
			return;

		ServerLevel otherLevel = level.getServer().getLevel(partner.dimension());
		if (otherLevel == null || !otherLevel.isLoaded(partner.pos()))
			return;

		if (otherLevel.getBlockEntity(partner.pos()) instanceof KineticBlockEntity partnerShaft
				&& partnerShaft instanceof PortalAccess partnerAccess) {
			partnerAccess.transferables$clearPortalConnection();
			partnerAccess.transferables$setPortalBinding(null);
			partnerShaft.sendData();

			if (breakPartner && PortalShaftLink.isPortalShaft(otherLevel.getBlockState(partner.pos()))) {
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
				.append(Component.translatable("transferables.portal_shaft.failed").withStyle(ChatFormatting.GOLD)),
				false);
		MutableComponent detail = failPos != null
				? Component.translatable("transferables.portal_shaft." + reason, failPos.getX(), failPos.getY(),
						failPos.getZ())
				: Component.translatable("transferables.portal_shaft." + reason);
		serverPlayer.displayClientMessage(Component.literal(" - ").withStyle(ChatFormatting.GRAY).append(detail), false);
	}
}
