package com.reggarf.mods.transferables.content.portal.kinetics;

import com.reggarf.mods.transferables.Transferables;
import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.content.portal.PortalPlacement;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Portal shaft lifecycle — mirrors Create TrackBlock:
 * only player-placed / non-portal-shaped sides initiate linking.
 */
@Mod.EventBusSubscriber(modid = Transferables.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalShaftEvents {
	private PortalShaftEvents() {}

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if (PortalPlacement.isSuppressed())
			return;
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level))
			return;
		if (PortalShaftLink.getPortalShaftAxis(event.getPlacedBlock()) == null)
			return;
		if (!(level.getBlockEntity(event.getPos()) instanceof KineticBlockEntity shaft))
			return;
		if (!(shaft instanceof PortalAccess access))
			return;

		access.transferables$setPlayerPlaced(true);
		PortalShaftBinding.connectToPortal(shaft, level);
	}

	@SubscribeEvent
	public static void onBlockBroken(BlockEvent.BreakEvent event) {
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
			return;
		if (PortalShaftLink.getPortalShaftAxis(event.getState()) == null)
			return;
		if (!(serverLevel.getBlockEntity(event.getPos()) instanceof KineticBlockEntity shaft))
			return;
		if (shaft instanceof PortalAccess access)
			PortalShaftBinding.onRemoved(shaft, access, serverLevel);
	}

	@SubscribeEvent
	public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
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
