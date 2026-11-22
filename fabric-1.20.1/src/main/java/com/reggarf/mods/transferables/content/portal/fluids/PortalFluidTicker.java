package com.reggarf.mods.transferables.content.portal.fluids;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import com.reggarf.mods.transferables.api.PortalBinding;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.mixin.PipeConnectionAccessor;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

		// Transfer limit respected according to RPM: max(1, RPM / 2) mB/tick (81 droplets/mB)
		int rateA = Math.max(1, (int) (speedA / 2f));
		int rateB = Math.max(1, (int) (speedB / 2f));
		int maxRateMB = Math.min(rateA, rateB);
		long maxRateDroplets = maxRateMB * (FluidConstants.BUCKET / 1000L); // 81L droplets per mB

		Direction sourceSide = towardPortal.getOpposite();
		Direction destSide = otherTowardPortal.getOpposite();

		List<Storage<FluidVariant>> sourceHandlers = findFluidHandlers(level, node.getPos(), sourceSide, 16);
		List<Storage<FluidVariant>> destHandlers = findFluidHandlers(otherLevel, partner.pos(), destSide, 16);

		if (sourceHandlers.isEmpty() || destHandlers.isEmpty())
			return;

		transferFluid(sourceHandlers, destHandlers, maxRateDroplets, node, towardPortal, otherNode, otherTowardPortal);
	}

	public static void transferFluid(List<Storage<FluidVariant>> sources, List<Storage<FluidVariant>> destinations,
	                                long maxRateDroplets, FluidTransportBehaviour nodeA, Direction dirA,
	                                FluidTransportBehaviour nodeB, Direction dirB) {
		for (Storage<FluidVariant> source : sources) {
			FluidVariant extractable = StorageUtil.findExtractableResource(source, null);
			if (extractable == null || extractable.isBlank())
				continue;

			for (Storage<FluidVariant> dest : destinations) {
				long moved = StorageUtil.move(source, dest, v -> v.equals(extractable), maxRateDroplets, null);
				if (moved > 0) {
					FluidStack transferred = new FluidStack(extractable.getFluid(), (int) Math.max(1, moved / (FluidConstants.BUCKET / 1000L)));
					updatePumpFlow(nodeA, dirA, transferred);
					updatePumpFlow(nodeB, dirB, transferred);
					return;
				}
			}
		}
	}

	public static List<Storage<FluidVariant>> findFluidHandlers(ServerLevel level, BlockPos pumpPos, Direction flowDir, int maxDepth) {
		List<Storage<FluidVariant>> results = new ArrayList<>();
		BlockPos directPos = pumpPos.relative(flowDir);

		// 1. Direct neighbor capability (Fluid Tank, etc.)
		Storage<FluidVariant> direct = FluidStorage.SIDED.find(level, directPos, flowDir.getOpposite());
		if (direct != null) {
			results.add(direct);
			return results;
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

						Storage<FluidVariant> handler = FluidStorage.SIDED.find(level, neighborPos, face.getOpposite());
						if (handler != null) {
							results.add(handler);
							visited.add(neighborPos);
							continue;
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
			Storage<FluidVariant> worldSource = FluidStorage.SIDED.find(level, directPos, flowDir.getOpposite());
			if (worldSource != null) {
				results.add(worldSource);
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
}
