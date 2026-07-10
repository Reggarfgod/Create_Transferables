package com.reggarf.mods.transferables.fluid;

import com.reggarf.mods.transferables.network.PortalFluidLink;
import com.simibubi.create.content.fluids.FlowSource;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * A pipe endpoint that bridges across a portal. Its handler is the shared bridge tank, so Create's
 * FluidNetwork fills it (when this side pushes) or drains it (when this side pulls). Resolves the
 * partner lazily every 20 ticks, so it also works if the far pipe is placed later.
 */
public class PortalFlowSource extends FlowSource {

	private final BlockPos selfPos;
	private final Direction portalDir;

	private LazyOptional<IFluidHandler> handler = LazyOptional.empty();
	private FluidTank currentBuffer;
	private long lastResolveTick = Long.MIN_VALUE;

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
		if (!(world instanceof ServerLevel level)) {
			cut();
			return;
		}
		if (handler.isPresent() && world.getGameTime() - lastResolveTick < 20)
			return;
		lastResolveTick = world.getGameTime();

		PortalFluidLink.PipeEndpoint partner = PortalFluidLink.resolvePipe(level, selfPos, portalDir);
		if (partner == null) {
			cut();
			return;
		}

		FluidTank buffer = PortalFluidBridges.get(level.dimension(), selfPos, partner.dim(), partner.pos());
		if (buffer != currentBuffer) {
			cut();
			currentBuffer = buffer;
			handler = LazyOptional.of(() -> buffer);
		}
	}

	@Override
	public LazyOptional<IFluidHandler> provideHandler() {
		return handler;
	}

	private void cut() {
		currentBuffer = null;
		if (handler.isPresent()) {
			LazyOptional<IFluidHandler> old = handler;
			handler = LazyOptional.empty();
			old.invalidate();
		}
	}
}