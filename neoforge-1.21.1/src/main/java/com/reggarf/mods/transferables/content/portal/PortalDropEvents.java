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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Auto-placed portal exits never drop items — only the block the player placed should.
 * <p>
 * Must read portal state from {@link BlockDropsEvent#getBlockEntity()} (not a world lookup):
 * by drop time the block is already removed from the level.
 */
@EventBusSubscriber(modid = Transferables.MODID)
public final class PortalDropEvents {
	/** Positions whose next drop must be suppressed (cascading partner break). */
	private static final Set<GlobalPos> SUPPRESS_DROPS = new HashSet<>();

	private PortalDropEvents() {}

	public static void suppressNextDrop(ResourceKey<Level> dimension, BlockPos pos) {
		SUPPRESS_DROPS.add(GlobalPos.of(dimension, pos.immutable()));
	}

	@SubscribeEvent
	public static void onBlockDrops(BlockDropsEvent event) {
		if (event.getLevel().isClientSide())
			return;

		GlobalPos at = GlobalPos.of(event.getLevel().dimension(), event.getPos());
		if (SUPPRESS_DROPS.remove(at)) {
			event.getDrops().clear();
			event.setDroppedExperience(0);
			return;
		}

		BlockEntity blockEntity = event.getBlockEntity();

		if (blockEntity instanceof KineticBlockEntity
				&& PortalShaftLink.getPortalShaftAxis(blockEntity.getBlockState()) != null
				&& blockEntity instanceof PortalAccess access
				&& !access.transferables$isPlayerPlaced()) {
			event.getDrops().clear();
			event.setDroppedExperience(0);
			return;
		}

		if (!PortalFluidLink.isPortalFluidNode(event.getState()))
			return;

		PortalPumpAccess access = fluidAccess(blockEntity);
		if (access != null && !access.transferables$isPlayerPlaced()) {
			event.getDrops().clear();
			event.setDroppedExperience(0);
		}
	}

	private static PortalPumpAccess fluidAccess(BlockEntity blockEntity) {
		if (!(blockEntity instanceof SmartBlockEntity smart))
			return null;
		FluidTransportBehaviour behaviour = smart.getBehaviour(FluidTransportBehaviour.TYPE);
		return behaviour instanceof PortalPumpAccess portal ? portal : null;
	}
}
