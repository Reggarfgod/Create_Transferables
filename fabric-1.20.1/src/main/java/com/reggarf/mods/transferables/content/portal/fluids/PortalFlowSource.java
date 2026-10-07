package com.reggarf.mods.transferables.content.portal.fluids;

import java.util.Iterator;
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

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidTank;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cross-portal fluid bridge.
 * <p>
 * Fluid may only enter the shared buffer from a real block tank
 * ({@link FluidHandler}). Open pipe ends are rejected so the bridge
 * cannot suck world fluid forever when the pump has no source.
 */
public class PortalFlowSource extends FlowSource {

	private final BlockPos selfPos;
	private final Direction portalDir;

	private @Nullable ServerLevel cachedLevel;
	private @Nullable Storage<FluidVariant> handler;
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

		if (handler == null || level.getGameTime() - lastResolveTick >= 20) {
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
			handler = new GatedBridgeTank(currentBuffer, this::mayFillBridge, this::mayDrainBridge);
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

		// Fabric Create: pressure/2 × 81 droplets (matches FluidNetwork transferSpeed)
		long rate = Math.max(1L, (long) (speed / 2f)) * 81L;
		BlockState state = level.getBlockState(selfPos);

		if (PortalFluidLink.isPortalPump(state)) {
			if (isPumpPullingFromPortal(level, state)) {
				ensureInboundPressure(self, rate / 81f);
			} else if (hasRealTankSource(level)) {
				pushFromTankIntoBuffer(level, self, rate);
			} else if (!currentBuffer.isEmpty()) {
				// No tank / open end only — do not keep feeding the far side from leftovers forever
				TransferUtil.clearStorage(currentBuffer);
			}
			return;
		}

		if (currentBuffer.isEmpty())
			return;
		ensureInboundPressure(self, rate / 81f);
		pushFromBufferToOutputs(level, self, rate);
	}

	private void pushFromTankIntoBuffer(ServerLevel level, FluidTransportBehaviour pump, long rate) {
		FlowSource source = tankSourceOnBack(level, pump);
		if (!(source instanceof FluidHandler))
			return;

		source.manageSource(level);
		Storage<FluidVariant> tank = source.provideHandler();
		if (tank == null)
			return;

		long space = currentBuffer.getCapacity() - currentBuffer.getFluidAmount();
		if (space <= 0)
			return;

		long amount = Math.min(rate, space);
		FluidStack simulated = TransferUtil.simulateExtractAnyFluid(tank, amount);
		if (simulated.isEmpty())
			return;

		try (Transaction t = TransferUtil.getTransaction()) {
			long canFill = currentBuffer.insert(simulated.getType(), simulated.getAmount(), t);
			if (canFill <= 0)
				return;
			long drained = tank.extract(simulated.getType(), canFill, t);
			if (drained > 0)
				t.commit();
		}
	}

	private void pushFromBufferToOutputs(ServerLevel level, FluidTransportBehaviour pipe, long rate) {
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

			Storage<FluidVariant> target = source.provideHandler();
			if (target == null)
				continue;

			FluidStack simulated = TransferUtil.simulateExtractAnyFluid(currentBuffer, rate);
			if (simulated.isEmpty())
				return;

			try (Transaction t = TransferUtil.getTransaction()) {
				long canFill = target.insert(simulated.getType(), simulated.getAmount(), t);
				if (canFill <= 0)
					continue;
				long drained = currentBuffer.extract(simulated.getType(), canFill, t);
				if (drained > 0)
					t.commit();
				return;
			}
		}
	}

	private boolean hasRealTankSource(ServerLevel level) {
		FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, selfPos, FluidTransportBehaviour.TYPE);
		if (pump == null)
			return false;
		FlowSource source = tankSourceOnBack(level, pump);
		if (!(source instanceof FluidHandler))
			return false;
		source.manageSource(level);
		Storage<FluidVariant> tank = source.provideHandler();
		return tank != null && !TransferUtil.simulateExtractAnyFluid(tank, 1).isEmpty();
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
	public Storage<FluidVariant> provideHandler() {
		return handler;
	}

	private void cut() {
		currentBuffer = null;
		handler = null;
	}

	private static final class GatedBridgeTank implements Storage<FluidVariant> {
		private final FluidTank inner;
		private final BooleanSupplier allowFill;
		private final BooleanSupplier allowDrain;

		private GatedBridgeTank(FluidTank inner, BooleanSupplier allowFill, BooleanSupplier allowDrain) {
			this.inner = inner;
			this.allowFill = allowFill;
			this.allowDrain = allowDrain;
		}

		@Override
		public long insert(FluidVariant resource, long maxAmount, TransactionContext ctx) {
			if (!allowFill.getAsBoolean())
				return 0;
			return inner.insert(resource, maxAmount, ctx);
		}

		@Override
		public long extract(FluidVariant resource, long maxAmount, TransactionContext ctx) {
			if (!allowDrain.getAsBoolean())
				return 0;
			return inner.extract(resource, maxAmount, ctx);
		}

		@Override
		public Iterator<StorageView<FluidVariant>> iterator() {
			return inner.iterator();
		}
	}
}
