package com.reggarf.mods.transferables.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.content.fluids.portal.PortalFlowSource;
import com.reggarf.mods.transferables.content.fluids.portal.PortalFluidLink;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.PipeConnection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

@Mixin(value = PipeConnection.class, remap = false)
public abstract class PipeConnectionMixin {

	@Shadow public Direction side;
	@Shadow Optional<FlowSource> source;

	@Inject(method = "determineSource", at = @At("HEAD"), cancellable = true)
	private void transferables$portalSource(Level world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!(world instanceof ServerLevel))
			return;
		if (!PortalFluidLink.isPortalPump(world.getBlockState(pos)))
			return;
		if (!PortalProvider.isSupportedPortal(world.getBlockState(pos.relative(side))))
			return;
		source = Optional.of(new PortalFlowSource(pos, side));
		cir.setReturnValue(true);
	}
}
