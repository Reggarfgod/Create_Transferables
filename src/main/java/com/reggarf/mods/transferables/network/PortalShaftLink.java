package com.reggarf.mods.transferables.network;

import javax.annotation.Nullable;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

public final class PortalShaftLink {
	private PortalShaftLink() {}

	@Nullable
	public static PortalTrackProvider.Exit resolve(ServerLevel level, BlockPos shaftPos, Direction towardPortal) {
		BlockPos portalPos = shaftPos.relative(towardPortal);
		Block portalBlock = level.getBlockState(portalPos).getBlock();

		PortalTrackProvider provider = PortalTrackProvider.REGISTRY.get(portalBlock);
		if (provider == null)
			return null;

		BlockFace inbound = new BlockFace(shaftPos, towardPortal);
		return provider.findExit(level, inbound);
	}
}
