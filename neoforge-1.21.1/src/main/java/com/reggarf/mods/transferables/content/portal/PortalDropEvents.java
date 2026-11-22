package com.reggarf.mods.transferables.content.portal;

import java.util.HashSet;
import java.util.Set;

import com.reggarf.mods.transferables.Transferables;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Suppresses drops for cascading partner block breaks across dimensions so items never duplicate
 * and are always dropped/picked up on the side the player mined or wrenched.
 */
@EventBusSubscriber(modid = Transferables.MODID)
public final class PortalDropEvents {
	/** Positions whose next drop must be suppressed (cascading partner break). */
	private static final Set<GlobalPos> SUPPRESS_DROPS = new HashSet<>();
	public static final ThreadLocal<Boolean> WRENCHING = ThreadLocal.withInitial(() -> false);

	private PortalDropEvents() {}

	public static boolean isWrenching() {
		return WRENCHING.get();
	}

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
		}
	}
}
