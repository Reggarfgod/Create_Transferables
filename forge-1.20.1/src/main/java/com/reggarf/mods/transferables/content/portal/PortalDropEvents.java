package com.reggarf.mods.transferables.content.portal;

import java.util.HashSet;
import java.util.Set;

import com.reggarf.mods.transferables.Transferables;
import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.content.portal.fluids.PortalFluidLink;
import com.reggarf.mods.transferables.content.portal.kinetics.PortalShaftLink;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Auto-placed portal exits never drop items — only the block the player placed should.
 * <p>
 * Forge 1.20.1 has no {@code BlockDropsEvent}; cancel the break and remove without drops.
 */
@Mod.EventBusSubscriber(modid = Transferables.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalDropEvents {
	/** Positions whose next drop must be suppressed (cascading partner break). */
	private static final Set<GlobalPos> SUPPRESS_DROPS = new HashSet<>();

	private PortalDropEvents() {}

	public static void suppressNextDrop(ResourceKey<Level> dimension, BlockPos pos) {
		SUPPRESS_DROPS.add(GlobalPos.of(dimension, pos.immutable()));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (event.isCanceled())
			return;
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level))
			return;

		BlockPos pos = event.getPos();
		GlobalPos at = GlobalPos.of(level.dimension(), pos);
		BlockState state = event.getState();
		BlockEntity blockEntity = level.getBlockEntity(pos);

		boolean suppress = SUPPRESS_DROPS.remove(at);

		if (!suppress && blockEntity instanceof KineticBlockEntity
				&& PortalShaftLink.getPortalShaftAxis(state) != null
				&& blockEntity instanceof PortalAccess access
				&& !access.transferables$isPlayerPlaced()) {
			suppress = true;
		}

		if (!suppress && PortalFluidLink.isPortalFluidNode(state)) {
			PortalPumpAccess access = fluidAccess(blockEntity);
			if (access != null && !access.transferables$isPlayerPlaced())
				suppress = true;
		}

		if (!suppress)
			return;

		event.setCanceled(true);
		event.setExpToDrop(0);
		level.removeBlock(pos, false);
	}

	private static PortalPumpAccess fluidAccess(BlockEntity blockEntity) {
		if (!(blockEntity instanceof SmartBlockEntity smart))
			return null;
		FluidTransportBehaviour behaviour = smart.getBehaviour(FluidTransportBehaviour.TYPE);
		return behaviour instanceof PortalPumpAccess portal ? portal : null;
	}
}
