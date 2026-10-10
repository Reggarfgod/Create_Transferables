package com.reggarf.mods.transferables.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = FluidPipeBlock.class, remap = false)
public abstract class FluidPipeBlockMixin {

	@Inject(method = "canConnectTo", at = @At("HEAD"), cancellable = true)
	private static void transferables$canConnectToPortal(BlockAndTintGetter world, BlockPos neighbourPos, BlockState neighbour,
	                                                     Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (PortalProvider.isSupportedPortal(neighbour)) {
			cir.setReturnValue(true);
		}
	}
}
