package com.reggarf.mods.transferables.portal;

import com.reggarf.mods.transferables.Transferables;
import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.network.PortalShaftLink;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Portal shaft lifecycle — mirrors {@code TrackBlock} placement tick and {@code updateShape} validation.
 */
@Mod.EventBusSubscriber(modid = Transferables.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalShaftEvents {
	private PortalShaftEvents() {}

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level))
			return;
		if (PortalShaftLink.getPortalShaftAxis(event.getPlacedBlock()) == null)
			return;
		if (!(level.getBlockEntity(event.getPos()) instanceof KineticBlockEntity shaft))
			return;
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

		PortalShaftBinding.connectToPortal(shaft, serverLevel);
		PortalShaftBinding.onPortalNeighborLost(shaft, access, serverLevel);
	}
}
