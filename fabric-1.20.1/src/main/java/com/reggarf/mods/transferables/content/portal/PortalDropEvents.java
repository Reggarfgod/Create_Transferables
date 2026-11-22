package com.reggarf.mods.transferables.content.portal;

import java.util.HashSet;
import java.util.Set;

import io.github.fabricators_of_create.porting_lib.event.common.BlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Suppresses drops for cascading partner block breaks across dimensions so items never duplicate
 * and are always dropped/picked up on the side the player mined or wrenched.
 */
public final class PortalDropEvents {
	/** Positions whose next drop must be suppressed (cascading partner break). */
	private static final Set<GlobalPos> SUPPRESS_DROPS = new HashSet<>();
	public static final ThreadLocal<Boolean> WRENCHING = ThreadLocal.withInitial(() -> false);

	private PortalDropEvents() {}

	public static boolean isWrenching() {
		return WRENCHING.get();
	}

	public static void register() {
		BlockEvents.BLOCK_BREAK.register(PortalDropEvents::onBlockBreak);
	}

	public static void suppressNextDrop(ResourceKey<Level> dimension, BlockPos pos) {
		SUPPRESS_DROPS.add(GlobalPos.of(dimension, pos.immutable()));
	}

	private static void onBlockBreak(BlockEvents.BreakEvent event) {
		if (event.isCanceled())
			return;
		if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level))
			return;
		if (WRENCHING.get())
			return;

		BlockPos pos = event.getPos();
		GlobalPos at = GlobalPos.of(level.dimension(), pos);
		if (SUPPRESS_DROPS.remove(at)) {
			event.setCanceled(true);
			event.setExpToDrop(0);
			level.removeBlock(pos, false);
		}
	}
}
