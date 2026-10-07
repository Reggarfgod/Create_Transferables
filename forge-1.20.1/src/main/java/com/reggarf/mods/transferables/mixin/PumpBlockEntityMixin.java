package com.reggarf.mods.transferables.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create pumps only treat tanks and open pipe ends as endpoints. Portal-facing mechanical pumps
 * must count as endpoints too or pressure never reaches the bridge.
 */
@Mixin(value = PumpBlockEntity.class, remap = false)
public abstract class PumpBlockEntityMixin {

	@Inject(method = "hasReachedValidEndpoint", at = @At("HEAD"), cancellable = true)
	private void transferables$portalPumpEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull,
	                                              CallbackInfoReturnable<Boolean> cir) {
		BlockPos pipePos = blockFace.getPos();
		Direction face = blockFace.getFace();
		BlockPos connectedPos = blockFace.getConnectedPos();
		BlockState connectedState = world.getBlockState(connectedPos);

		// Pump/pipe facing directly into a portal counts as a valid fluid endpoint
		if (PortalProvider.isSupportedPortal(connectedState)) {
			FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, pipePos);
			if (pipe != null && pipe.canHaveFlowToward(world.getBlockState(pipePos), face)) {
				cir.setReturnValue(true);
				return;
			}
		}

		// Neighbor is a portal-bound pipe/pump that opens toward a portal
		FluidTransportBehaviour connected = FluidPropagator.getPipe(world, connectedPos);
		if (connected != null && connected.canHaveFlowToward(connectedState, face.getOpposite())) {
			for (Direction towardPortal : Direction.values()) {
				if (towardPortal == face.getOpposite())
					continue;
				if (!PortalProvider.isSupportedPortal(world.getBlockState(connectedPos.relative(towardPortal))))
					continue;
				if (connected.canHaveFlowToward(connectedState, towardPortal)) {
					cir.setReturnValue(true);
					return;
				}
			}
		}
	}
}
