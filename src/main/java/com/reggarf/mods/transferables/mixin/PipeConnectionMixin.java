package com.reggarf.mods.transferables.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.content.portal.fluids.PortalFlowSource;
import com.reggarf.mods.transferables.content.portal.fluids.PortalFluidLink;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = PipeConnection.class, remap = false)
public abstract class PipeConnectionMixin {

	@Shadow public Direction side;
	@Shadow Optional<FlowSource> source;

	@Inject(method = "determineSource", at = @At("HEAD"), cancellable = true)
	private void transferables$portalSource(Level world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!(world instanceof ServerLevel))
			return;
		if (!tryAttachPortalSource(world, pos))
			return;
		cir.setReturnValue(true);
	}

	@Inject(method = "manageSource", at = @At("HEAD"))
	private void transferables$keepPortalSource(Level world, BlockPos pos, BlockEntity blockEntity, CallbackInfo ci) {
		if (!(world instanceof ServerLevel))
			return;
		// Create often sets OpenEndedPipe into the portal; force our bridge source back
		if (!(source.orElse(null) instanceof PortalFlowSource))
			tryAttachPortalSource(world, pos);
	}

	private boolean tryAttachPortalSource(Level world, BlockPos pos) {
		if (!PortalProvider.isSupportedPortal(world.getBlockState(pos.relative(side))))
			return false;

		BlockState state = world.getBlockState(pos);
		if (PortalFluidLink.isPortalPump(state)) {
			source = Optional.of(new PortalFlowSource(pos, side));
			return true;
		}

		if (PortalFluidLink.isPortalPipe(state)) {
			FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, pos);
			if (!(pipe instanceof PortalPumpAccess access) || !access.transferables$isPumpPortalConnected())
				return false;
			if (access.transferables$getPumpPortalDirection() != side)
				return false;
			source = Optional.of(new PortalFlowSource(pos, side));
			return true;
		}
		return false;
	}
}
