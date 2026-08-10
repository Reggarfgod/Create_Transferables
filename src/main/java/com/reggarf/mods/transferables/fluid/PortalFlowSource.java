package com.reggarf.mods.transferables.fluid;

import com.reggarf.mods.transferables.network.PortalFluidLink;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * Pump endpoint that bridges across a portal through a shared buffer tank.
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
		if (!PumpBlock.isPump(level.getBlockState(selfPos))) {
			cut();
			return;
		}
		if (handler.isPresent() && world.getGameTime() - lastResolveTick < 20)
			return;
		lastResolveTick = world.getGameTime();

		PortalFluidLink.PumpEndpoint partner = PortalFluidLink.resolvePump(level, selfPos, portalDir);
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

		applyReceivePressure(level);
	}

	private void applyReceivePressure(ServerLevel level) {
		if (currentBuffer == null || currentBuffer.isEmpty())
			return;
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, selfPos, FluidTransportBehaviour.TYPE);
		if (pump == null)
			return;
		PipeConnection conn = pump.getConnection(portalDir);
		if (conn == null)
			return;
		Couple<Float> pressure = conn.getPressure();
		if (pressure.getFirst() > 0 || pressure.getSecond() > 0)
			return;
		pump.addPressure(portalDir, true, 32f);
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
