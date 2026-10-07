package com.reggarf.mods.transferables.content.portal.kinetics;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.content.portal.PortalPlacement;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import io.github.fabricators_of_create.porting_lib.event.common.BlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Portal shaft lifecycle — mirrors Create TrackBlock:
 * only player-placed / non-portal-shaped sides initiate linking.
 */
public final class PortalShaftEvents {
	private PortalShaftEvents() {}

	public static void register() {
		BlockEvents.POST_PROCESS_PLACE.register(PortalShaftEvents::onBlockPlaced);
		BlockEvents.BLOCK_BREAK.register(PortalShaftEvents::onBlockBroken);
		BlockEvents.NEIGHBORS_NOTIFY.register(PortalShaftEvents::onNeighborNotify);
	}

	private static void onBlockPlaced(BlockPlaceContext context, BlockPos pos, BlockState placed) {
		if (PortalPlacement.isSuppressed())
			return;
		Level level = context.getLevel();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel))
			return;
		if (PortalShaftLink.getPortalShaftAxis(placed) == null)
			return;
		if (!(serverLevel.getBlockEntity(pos) instanceof KineticBlockEntity shaft))
			return;
		if (!(shaft instanceof PortalAccess access))
			return;

		access.transferables$setPlayerPlaced(true);
		PortalShaftBinding.connectToPortal(shaft, serverLevel);
	}

	private static void onBlockBroken(BlockEvents.BreakEvent event) {
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
			return;
		if (PortalShaftLink.getPortalShaftAxis(event.getState()) == null)
			return;
		if (!(serverLevel.getBlockEntity(event.getPos()) instanceof KineticBlockEntity shaft))
			return;
		if (shaft instanceof PortalAccess access)
			PortalShaftBinding.onRemoved(shaft, access, serverLevel);
	}

	private static void onNeighborNotify(BlockEvents.NeighborNotifyEvent event) {
		if (PortalPlacement.isSuppressed())
			return;

		Level level = (Level) event.getLevel();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel))
			return;

		BlockState state = level.getBlockState(event.getPos());
		if (PortalShaftLink.getPortalShaftAxis(state) == null)
			return;
		if (!(level.getBlockEntity(event.getPos()) instanceof KineticBlockEntity shaft))
			return;
		if (!(shaft instanceof PortalAccess access))
			return;

		// Auto-placed exits never initiate (Create portal-shaped tracks skip connect)
		if (access.transferables$isPlayerPlaced())
			PortalShaftBinding.connectToPortal(shaft, serverLevel);

		PortalShaftBinding.onPortalNeighborLost(shaft, access, serverLevel);
	}
}
