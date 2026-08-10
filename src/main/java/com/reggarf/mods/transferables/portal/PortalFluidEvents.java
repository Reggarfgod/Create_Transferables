package com.reggarf.mods.transferables.portal;

import com.reggarf.mods.transferables.Transferables;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.network.PortalFluidLink;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Portal pump lifecycle — mirrors {@link PortalShaftEvents}. */
@Mod.EventBusSubscriber(modid = Transferables.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalFluidEvents {
	private PortalFluidEvents() {}

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level))
			return;
		if (!PortalFluidLink.isPortalPump(event.getPlacedBlock()))
			return;
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, event.getPos(), FluidTransportBehaviour.TYPE);
		if (pump != null)
			PortalFluidBinding.connectToPortal(pump, level);
	}

	@SubscribeEvent
	public static void onBlockBroken(BlockEvent.BreakEvent event) {
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
			return;
		if (!PortalFluidLink.isPortalPump(event.getState()))
			return;
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(serverLevel, event.getPos(),
				FluidTransportBehaviour.TYPE);
		if (pump instanceof PortalPumpAccess access)
			PortalFluidBinding.onRemoved(pump, access, serverLevel);
	}

	@SubscribeEvent
	public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
		Level level = (Level) event.getLevel();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel))
			return;

		BlockState state = level.getBlockState(event.getPos());
		if (!PortalFluidLink.isPortalPump(state))
			return;
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(serverLevel, event.getPos(),
				FluidTransportBehaviour.TYPE);
		if (!(pump instanceof PortalPumpAccess access))
			return;

		PortalFluidBinding.connectToPortal(pump, serverLevel);
		PortalFluidBinding.onPortalNeighborLost(pump, access, serverLevel);
	}
}
