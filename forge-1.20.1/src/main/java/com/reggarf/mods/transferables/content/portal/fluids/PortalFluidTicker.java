package com.reggarf.mods.transferables.content.portal.fluids;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;

import com.reggarf.mods.transferables.api.PortalBinding;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.mixin.PipeConnectionAccessor;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Executes fluid transfer across Nether Portals using Mechanical Pumps on both sides.
 * Fluid flows from the source-side pump (pushing into portal) to the destination-side
 * pump (pulling from portal). Both pumps must have rotational power and respect RPM limits.
 */
public final class PortalFluidTicker {
	private PortalFluidTicker() {}

	public static void tick(FluidTransportBehaviour node, ServerLevel level) {
		if (!(node instanceof PortalPumpAccess access) || !access.transferables$isPumpPortalConnected())
			return;

		Direction towardPortal = access.transferables$getPumpPortalDirection();
		if (towardPortal == null)
			return;

		if (!PortalFluidLink.isFacingPortal(level, node.getPos(), towardPortal))
			return;

		if (!(node.blockEntity instanceof PumpBlockEntity pumpA))
			return;

		BlockState stateA = node.blockEntity.getBlockState();
		if (!PortalFluidLink.isPortalPump(stateA))
			return;

		// Create pump: front pushes, back pulls.
		// If facing the portal with front, pump is PUSHING into the portal.
		boolean pumpAPullingFromPortal = pumpA.isPullingOnSide(towardPortal == stateA.getValue(PumpBlock.FACING));
		if (pumpAPullingFromPortal) {
			// Destination side: driven by the source pump tick to avoid double-processing.
			return;
		}

		// Pump A is PUSHING into the portal -> It is the SOURCE pump!
		PortalBinding partner = access.transferables$getPumpBoundPartner();
		if (partner == null)
			return;

		ServerLevel otherLevel = level.getServer().getLevel(partner.dimension());
		if (otherLevel == null || !otherLevel.isLoaded(partner.pos()))
			return;

		BlockEntity otherBE = otherLevel.getBlockEntity(partner.pos());
		if (!(otherBE instanceof PumpBlockEntity pumpB))
			return;

		FluidTransportBehaviour otherNode = pumpB.getBehaviour(FluidTransportBehaviour.TYPE);
		if (!(otherNode instanceof PortalPumpAccess otherAccess) || !otherAccess.transferables$isPumpPortalConnected())
			return;

		Direction otherTowardPortal = otherAccess.transferables$getPumpPortalDirection();
		if (otherTowardPortal == null)
			return;

		BlockState stateB = otherBE.getBlockState();
		if (!PortalFluidLink.isPortalPump(stateB))
			return;

		// Verify pumpB is PULLING from the portal
		boolean partnerPullingFromPortal = pumpB.isPullingOnSide(otherTowardPortal == stateB.getValue(PumpBlock.FACING));
		if (!partnerPullingFromPortal) {
			// Partner pump is not configured to pull fluid from the portal
			return;
		}

		// Both pumps must work with Create rotational power (RPM > 0)
		float speedA = Math.abs(pumpA.getSpeed());
		float speedB = Math.abs(pumpB.getSpeed());
		if (speedA <= 0 || speedB <= 0)
			return;

		// Transfer limit respected according to RPM: max(1, RPM / 2) mB/tick
		int rateA = Math.max(1, (int) (speedA / 2f));
		int rateB = Math.max(1, (int) (speedB / 2f));
		int maxRate = Math.min(rateA, rateB);

		Direction sourceSide = towardPortal.getOpposite();
		Direction destSide = otherTowardPortal.getOpposite();

		List<IFluidHandler> sourceHandlers = findFluidHandlers(level, node.getPos(), sourceSide, 16);
		List<IFluidHandler> destHandlers = findFluidHandlers(otherLevel, partner.pos(), destSide, 16);

		if (sourceHandlers.isEmpty() || destHandlers.isEmpty())
			return;

		FluidStack transferred = transferFluid(sourceHandlers, destHandlers, maxRate);

		if (!transferred.isEmpty()) {
			updatePumpFlow(node, towardPortal, transferred);
			updatePumpFlow(otherNode, otherTowardPortal, transferred);
		}
	}

	public static FluidStack transferFluid(List<IFluidHandler> sources, List<IFluidHandler> destinations, int maxRate) {
		for (IFluidHandler source : sources) {
			FluidStack simDrain = source.drain(maxRate, FluidAction.SIMULATE);
			if (simDrain.isEmpty() || simDrain.getAmount() <= 0)
				continue;

			for (IFluidHandler dest : destinations) {
				int simFill = dest.fill(simDrain, FluidAction.SIMULATE);
				if (simFill <= 0)
					continue;

				int amountToMove = Math.min(simDrain.getAmount(), simFill);
				FluidStack execDrain = source.drain(amountToMove, FluidAction.EXECUTE);
				if (execDrain.isEmpty() || execDrain.getAmount() <= 0)
					continue;

				int actualFilled = dest.fill(execDrain, FluidAction.EXECUTE);
				if (actualFilled < execDrain.getAmount()) {
					FluidStack refund = execDrain.copy();
					refund.shrink(actualFilled);
					source.fill(refund, FluidAction.EXECUTE);
				}

				FluidStack transferred = execDrain.copy();
				transferred.setAmount(actualFilled);
				return transferred;
			}
		}
		return FluidStack.EMPTY;
	}

	public static List<IFluidHandler> findFluidHandlers(ServerLevel level, BlockPos pumpPos, Direction flowDir, int maxDepth) {
		List<IFluidHandler> results = new ArrayList<>();
		BlockPos directPos = pumpPos.relative(flowDir);

		// 1. Direct neighbor capability (Fluid Tank, etc.)
		BlockEntity directBE = level.getBlockEntity(directPos);
		if (directBE != null) {
			IFluidHandler direct = directBE.getCapability(ForgeCapabilities.FLUID_HANDLER, flowDir.getOpposite()).resolve().orElse(null);
			if (direct != null) {
				results.add(direct);
				return results;
			}
		}

		// 2. If direct neighbor is a pipe, do a BFS to find connected tanks
		FluidTransportBehaviour directPipe = FluidPropagator.getPipe(level, directPos);
		if (directPipe != null) {
			Set<BlockPos> visited = new HashSet<>();
			Queue<BlockPos> queue = new ArrayDeque<>();
			visited.add(pumpPos);
			visited.add(directPos);
			queue.add(directPos);

			int depth = 0;
			while (!queue.isEmpty() && depth < maxDepth) {
				int size = queue.size();
				for (int i = 0; i < size; i++) {
					BlockPos currentPos = queue.poll();
					BlockState currentState = level.getBlockState(currentPos);
					FluidTransportBehaviour currentPipe = FluidPropagator.getPipe(level, currentPos);
					if (currentPipe == null)
						continue;

					for (Direction face : FluidPropagator.getPipeConnections(currentState, currentPipe)) {
						BlockPos neighborPos = currentPos.relative(face);
						if (visited.contains(neighborPos))
							continue;

						BlockEntity neighborBE = level.getBlockEntity(neighborPos);
						if (neighborBE != null) {
							IFluidHandler handler = neighborBE.getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite()).resolve().orElse(null);
							if (handler != null) {
								results.add(handler);
								visited.add(neighborPos);
								continue;
							}
						}

						FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, neighborPos);
						if (nextPipe != null) {
							visited.add(neighborPos);
							queue.add(neighborPos);
						}
					}
				}
				depth++;
				if (!results.isEmpty())
					break;
			}
		}

		// 3. Fluid source block in the world (e.g. water or lava source block)
		if (results.isEmpty()) {
			FluidState fluidState = level.getFluidState(directPos);
			if (fluidState != null && fluidState.isSource()) {
				results.add(new WorldFluidSourceHandler(level, directPos, fluidState));
			}
		}

		return results;
	}

	private static void updatePumpFlow(FluidTransportBehaviour node, Direction towardPortal, FluidStack fluid) {
		if (node == null || towardPortal == null || fluid.isEmpty())
			return;
		PipeConnection conn = node.getConnection(towardPortal);
		if (conn instanceof PipeConnectionAccessor accessor) {
			Optional<PipeConnection.Flow> currentFlow = accessor.transferables$getFlow();
			if (currentFlow == null || currentFlow.isEmpty() || !conn.getProvidedFluid().isFluidEqual(fluid)) {
				accessor.transferables$setFlow(Optional.of(conn.new Flow(false, fluid.copy())));
			}
			accessor.transferables$getFlow().ifPresent(flow -> {
				flow.complete = true;
				flow.fluid = fluid.copy();
			});
		}
	}

	private static class WorldFluidSourceHandler implements IFluidHandler {
		private final ServerLevel level;
		private final BlockPos pos;
		private final FluidState fluidState;

		public WorldFluidSourceHandler(ServerLevel level, BlockPos pos, FluidState fluidState) {
			this.level = level;
			this.pos = pos;
			this.fluidState = fluidState;
		}

		@Override
		public int getTanks() {
			return 1;
		}

		@Nonnull
		@Override
		public FluidStack getFluidInTank(int tank) {
			return new FluidStack(fluidState.getType(), 1000);
		}

		@Override
		public int getTankCapacity(int tank) {
			return 1000;
		}

		@Override
		public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
			return false;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Nonnull
		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (resource.isEmpty() || !resource.isFluidEqual(getFluidInTank(0)))
				return FluidStack.EMPTY;
			return drain(resource.getAmount(), action);
		}

		@Nonnull
		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (maxDrain < 1000)
				return FluidStack.EMPTY;
			if (action.execute()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
			return new FluidStack(fluidState.getType(), 1000);
		}
	}
}
