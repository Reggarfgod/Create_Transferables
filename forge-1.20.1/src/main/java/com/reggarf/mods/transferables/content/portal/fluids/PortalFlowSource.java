package com.reggarf.mods.transferables.content.portal.fluids;

import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.reggarf.mods.transferables.api.PortalBinding;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.mixin.PipeConnectionAccessor;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * Cross-portal fluid bridge.
 * <p>
 * Fluid may only enter the shared buffer from a real block tank
 * ({@link FlowSource.FluidHandler}). Open pipe ends are rejected so the bridge
 * cannot suck world fluid forever when the pump has no source.
 */
public class PortalFlowSource extends FlowSource {

	private final BlockPos selfPos;
	private final Direction portalDir;

	private @Nullable ServerLevel cachedLevel;
	private LazyOptional<IFluidHandler> handler = LazyOptional.empty();
	private @Nullable FluidTank currentBuffer;
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
		cachedLevel = level;

		if (!PortalFluidLink.isPortalFluidNode(level.getBlockState(selfPos))) {
			cut();
			return;
		}

		if (!handler.isPresent() || level.getGameTime() - lastResolveTick >= 20) {
			lastResolveTick = level.getGameTime();
			if (!resolveBuffer(level)) {
				cut();
				return;
			}
		}

		transferThroughBridge(level);
	}

	@Override
	public void whileFlowPresent(Level world, boolean pulling) {
		if (world instanceof ServerLevel level && currentBuffer != null) {
			cachedLevel = level;
			transferThroughBridge(level);
		}
	}

	private boolean resolveBuffer(ServerLevel level) {
		PortalFluidLink.FluidEndpoint partner = PortalFluidLink.resolvePartner(level, selfPos, portalDir);
		if (partner == null)
			return false;

		FluidTank buffer = PortalFluidBridges.get(level.dimension(), selfPos, partner.dim(), partner.pos());
		if (buffer != currentBuffer) {
			currentBuffer = buffer;
			if (handler.isPresent()) {
				LazyOptional<IFluidHandler> old = handler;
				handler = LazyOptional.empty();
				old.invalidate();
			}
			GatedBridgeTank gated = new GatedBridgeTank(currentBuffer, this::mayFillBridge, this::mayDrainBridge);
			handler = LazyOptional.of(() -> gated);
		}
		return true;
	}

	/** Create FluidNetwork may fill the bridge only from a pump that has a real tank. */
	private boolean mayFillBridge() {
		if (currentBuffer == null || cachedLevel == null)
			return false;
		ServerLevel level = cachedLevel;
		BlockState state = level.getBlockState(selfPos);
		if (!PortalFluidLink.isPortalPump(state))
			return false;
		if (isPumpPullingFromPortal(level, state))
			return false;
		if (pumpSpeed(level) <= 0)
			return false;
		return hasRealTankSource(level);
	}

	/** Drain only while the driving pump is powered and the buffer still has fluid. */
	private boolean mayDrainBridge() {
		if (currentBuffer == null || currentBuffer.isEmpty())
			return false;
		if (cachedLevel == null)
			return false;
		return pumpSpeed(cachedLevel) > 0;
	}

	private void transferThroughBridge(ServerLevel level) {
		if (currentBuffer == null)
			return;

		FluidTransportBehaviour self = BlockEntityBehaviour.get(level, selfPos, FluidTransportBehaviour.TYPE);
		if (self == null)
			return;

		float speed = pumpSpeed(level);
		if (speed <= 0)
			return;

		int rate = Math.max(1, (int) (speed / 2f));
		BlockState state = level.getBlockState(selfPos);

		if (PortalFluidLink.isPortalPump(state)) {
			if (isPumpPullingFromPortal(level, state)) {
				ensureInboundPressure(self, rate);
			} else if (hasRealTankSource(level)) {
				pushFromTankIntoBuffer(level, self, rate);
			} else if (!currentBuffer.isEmpty()) {
				// No tank / open end only — do not keep feeding the far side from leftovers forever
				currentBuffer.drain(currentBuffer.getCapacity(), FluidAction.EXECUTE);
			}
			return;
		}

		if (currentBuffer.isEmpty())
			return;
		ensureInboundPressure(self, rate);
		pushFromBufferToOutputs(level, self, rate);
	}

	private void pushFromTankIntoBuffer(ServerLevel level, FluidTransportBehaviour pump, int rate) {
		FlowSource source = tankSourceOnBack(level, pump);
		if (!(source instanceof FlowSource.FluidHandler))
			return;

		source.manageSource(level);
		IFluidHandler tank = source.provideHandler().orElse(null);
		if (tank == null)
			return;

		int space = currentBuffer.getCapacity() - currentBuffer.getFluidAmount();
		if (space <= 0)
			return;

		int amount = Math.min(rate, space);
		FluidStack simulated = tank.drain(amount, FluidAction.SIMULATE);
		if (simulated.isEmpty())
			return;

		int canFill = currentBuffer.fill(simulated, FluidAction.SIMULATE);
		if (canFill <= 0)
			return;

		FluidStack drained = tank.drain(canFill, FluidAction.EXECUTE);
		if (!drained.isEmpty())
			currentBuffer.fill(drained, FluidAction.EXECUTE);
	}

	private void pushFromBufferToOutputs(ServerLevel level, FluidTransportBehaviour pipe, int rate) {
		for (Direction side : Iterate.directions) {
			if (side == portalDir)
				continue;
			PipeConnection conn = pipe.getConnection(side);
			if (conn == null)
				continue;

			if (((PipeConnectionAccessor) conn).transferables$getSource().isEmpty())
				conn.determineSource(level, selfPos);

			FlowSource source = optionalSource(conn);
			if (source == null || !source.isEndpoint())
				continue;
			source.manageSource(level);

			IFluidHandler target = source.provideHandler().orElse(null);
			if (target == null)
				continue;

			FluidStack simulated = currentBuffer.drain(rate, FluidAction.SIMULATE);
			if (simulated.isEmpty())
				return;

			int canFill = target.fill(simulated, FluidAction.SIMULATE);
			if (canFill <= 0)
				continue;

			FluidStack drained = currentBuffer.drain(canFill, FluidAction.EXECUTE);
			if (!drained.isEmpty())
				target.fill(drained, FluidAction.EXECUTE);
			return;
		}
	}

	private boolean hasRealTankSource(ServerLevel level) {
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, selfPos, FluidTransportBehaviour.TYPE);
		if (pump == null)
			return false;
		FlowSource source = tankSourceOnBack(level, pump);
		if (!(source instanceof FlowSource.FluidHandler))
			return false;
		source.manageSource(level);
		IFluidHandler tank = source.provideHandler().orElse(null);
		return tank != null && !tank.drain(1, FluidAction.SIMULATE).isEmpty();
	}

	@Nullable
	private FlowSource tankSourceOnBack(ServerLevel level, FluidTransportBehaviour pump) {
		PipeConnection back = pump.getConnection(portalDir.getOpposite());
		if (back == null)
			return null;
		if (((PipeConnectionAccessor) back).transferables$getSource().isEmpty())
			back.determineSource(level, selfPos);
		FlowSource source = optionalSource(back);
		if (source instanceof OpenEndedPipe)
			return null;
		return source;
	}

	@Nullable
	private static FlowSource optionalSource(PipeConnection conn) {
		return ((PipeConnectionAccessor) conn).transferables$getSource().orElse(null);
	}

	private void ensureInboundPressure(FluidTransportBehaviour pipe, float pressure) {
		PipeConnection conn = pipe.getConnection(portalDir);
		if (conn == null)
			return;
		float inbound = conn.getPressure().getFirst();
		if (inbound < pressure)
			pipe.addPressure(portalDir, true, pressure - inbound);
	}

	private boolean isPumpPullingFromPortal(ServerLevel level, BlockState state) {
		if (!(level.getBlockEntity(selfPos) instanceof PumpBlockEntity pump))
			return false;
		Direction front = state.getValue(PumpBlock.FACING);
		return pump.isPullingOnSide(portalDir == front);
	}

	private float pumpSpeed(ServerLevel level) {
		BlockState state = level.getBlockState(selfPos);
		if (PortalFluidLink.isPortalPump(state)
				&& level.getBlockEntity(selfPos) instanceof PumpBlockEntity pump)
			return Math.abs(pump.getSpeed());

		FluidTransportBehaviour self = BlockEntityBehaviour.get(level, selfPos, FluidTransportBehaviour.TYPE);
		if (self instanceof PortalPumpAccess access) {
			PortalBinding partner = access.transferables$getPumpBoundPartner();
			if (partner != null) {
				ServerLevel other = level.getServer().getLevel(partner.dimension());
				if (other != null && other.isLoaded(partner.pos())
						&& other.getBlockEntity(partner.pos()) instanceof PumpBlockEntity pump)
					return Math.abs(pump.getSpeed());
			}
		}
		return 0f;
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

	private static final class GatedBridgeTank implements IFluidHandler {
		private final FluidTank inner;
		private final BooleanSupplier allowFill;
		private final BooleanSupplier allowDrain;

		private GatedBridgeTank(FluidTank inner, BooleanSupplier allowFill, BooleanSupplier allowDrain) {
			this.inner = inner;
			this.allowFill = allowFill;
			this.allowDrain = allowDrain;
		}

		@Override
		public int getTanks() {
			return inner.getTanks();
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return inner.getFluidInTank(tank);
		}

		@Override
		public int getTankCapacity(int tank) {
			return inner.getTankCapacity(tank);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return allowFill.getAsBoolean() && inner.isFluidValid(tank, stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (!allowFill.getAsBoolean())
				return 0;
			return inner.fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (!allowDrain.getAsBoolean())
				return FluidStack.EMPTY;
			return inner.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (!allowDrain.getAsBoolean())
				return FluidStack.EMPTY;
			return inner.drain(maxDrain, action);
		}
	}
}
