package com.reggarf.mods.transferables.content.portal.fluids;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public final class PortalFluidBridges {
	private PortalFluidBridges() {}

	public static final int BUFFER_CAPACITY = 8000;
	private static final long TTL_MILLIS = 60_000L;

	private record Key(String a, String b) {}

	private static final class Bridge {
		final FluidTank tank = new FluidTank(BUFFER_CAPACITY);
		long lastAccess = System.currentTimeMillis();
	}

	private static final Map<Key, Bridge> BRIDGES = new HashMap<>();

	/**
	 * Clears leftover bridge fluid for a pump↔pipe pair so disconnected pumps
	 * cannot keep emptying an old buffer.
	 */
	public static void clear(ResourceKey<Level> d1, BlockPos p1, ResourceKey<Level> d2, BlockPos p2) {
		Bridge bridge = BRIDGES.remove(key(d1, p1, d2, p2));
		if (bridge != null)
			bridge.tank.drain(bridge.tank.getCapacity(),
					net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
	}

	public static FluidTank get(ResourceKey<Level> d1, BlockPos p1, ResourceKey<Level> d2, BlockPos p2) {
		sweep();
		Bridge bridge = BRIDGES.computeIfAbsent(key(d1, p1, d2, p2), k -> new Bridge());
		bridge.lastAccess = System.currentTimeMillis();
		return bridge.tank;
	}

	private static Key key(ResourceKey<Level> d1, BlockPos p1, ResourceKey<Level> d2, BlockPos p2) {
		String s1 = d1.location() + "@" + p1.asLong();
		String s2 = d2.location() + "@" + p2.asLong();
		return s1.compareTo(s2) <= 0 ? new Key(s1, s2) : new Key(s2, s1);
	}

	private static void sweep() {
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Key, Bridge>> it = BRIDGES.entrySet().iterator(); it.hasNext();) {
			Bridge b = it.next().getValue();
			if (now - b.lastAccess > TTL_MILLIS && b.tank.isEmpty())
				it.remove();
		}
	}
}
