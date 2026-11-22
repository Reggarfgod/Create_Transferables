package com.reggarf.mods.transferables.content.portal.fluids;

import com.simibubi.create.content.fluids.FlowSource;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Cross-portal fluid bridge endpoint.
 * Fluid transfer between portal pumps is managed by {@link PortalFluidTicker}.
 */
public class PortalFlowSource extends FlowSource {

	private final BlockPos selfPos;
	private final Direction portalDir;

	public PortalFlowSource(BlockPos pos, Direction portalDir) {
		super(new BlockFace(pos, portalDir));
		this.selfPos = pos.immutable();
		this.portalDir = portalDir;
	}

	@Override
	public boolean isEndpoint() {
		return true;
	}

	@Override
	public void manageSource(Level world) {
	}

	@Override
	public void whileFlowPresent(Level world, boolean pulling) {
	}
}
