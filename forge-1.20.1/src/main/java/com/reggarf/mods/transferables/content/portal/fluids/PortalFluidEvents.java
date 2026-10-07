package com.reggarf.mods.transferables.content.portal.fluids;

import com.reggarf.mods.transferables.Transferables;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.content.portal.PortalPlacement;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Player-placed pumps initiate the portal link; exit pipes only clean up / break with the portal.
 */
@Mod.EventBusSubscriber(modid = Transferables.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalFluidEvents {
	private PortalFluidEvents() {}

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if (PortalPlacement.isSuppressed())
			return;
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level))
			return;
		if (!PortalFluidLink.isPortalPump(event.getPlacedBlock()))
			return;
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, event.getPos(), FluidTransportBehaviour.TYPE);
		if (pump != null) {
			if (pump instanceof PortalPumpAccess access)
				access.transferables$setPlayerPlaced(true);
			PortalFluidBinding.connectToPortal(pump, level);
		}
	}

	@SubscribeEvent
	public static void onBlockBroken(BlockEvent.BreakEvent event) {
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

	@SubscribeEvent
	public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
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
