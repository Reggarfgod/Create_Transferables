package com.reggarf.mods.transferables.content.portal.fluids;

import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.content.portal.PortalPlacement;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import io.github.fabricators_of_create.porting_lib.event.common.BlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Player-placed pumps initiate the portal link; exit pipes only clean up / break with the portal.
 */
public final class PortalFluidEvents {
	private PortalFluidEvents() {}

	public static void register() {
		BlockEvents.POST_PROCESS_PLACE.register(PortalFluidEvents::onBlockPlaced);
		BlockEvents.BLOCK_BREAK.register(PortalFluidEvents::onBlockBroken);
		BlockEvents.NEIGHBORS_NOTIFY.register(PortalFluidEvents::onNeighborNotify);
	}

	private static void onBlockPlaced(BlockPlaceContext context, BlockPos pos, BlockState placed) {
		if (PortalPlacement.isSuppressed())
			return;
		Level level = context.getLevel();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel))
			return;
		if (!PortalFluidLink.isPortalPump(placed))
			return;
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(serverLevel, pos, FluidTransportBehaviour.TYPE);
		if (pump != null) {
			if (pump instanceof PortalPumpAccess access)
				access.transferables$setPlayerPlaced(true);
			PortalFluidBinding.connectToPortal(pump, serverLevel);
		}
	}

	private static void onBlockBroken(BlockEvents.BreakEvent event) {
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
			return;
		BlockState state = event.getState();
		if (!PortalFluidLink.isPortalFluidNode(state))
			return;
		FluidTransportBehaviour node = BlockEntityBehaviour.get(serverLevel, event.getPos(),
				FluidTransportBehaviour.TYPE);
		if (node instanceof PortalPumpAccess access && access.transferables$isPumpPortalConnected())
			PortalFluidBinding.onRemoved(node, access, serverLevel);
	}

	private static void onNeighborNotify(BlockEvents.NeighborNotifyEvent event) {
		if (PortalPlacement.isSuppressed())
			return;

		Level level = (Level) event.getLevel();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel))
			return;

		BlockState state = level.getBlockState(event.getPos());
		if (!PortalFluidLink.isPortalFluidNode(state))
			return;
		FluidTransportBehaviour node = BlockEntityBehaviour.get(serverLevel, event.getPos(),
				FluidTransportBehaviour.TYPE);
		if (!(node instanceof PortalPumpAccess access))
			return;

		if (PortalFluidLink.isPortalPump(state) && access.transferables$isPlayerPlaced())
			PortalFluidBinding.connectToPortal(node, serverLevel);

		PortalFluidBinding.onPortalNeighborLost(node, access, serverLevel);
	}
}
