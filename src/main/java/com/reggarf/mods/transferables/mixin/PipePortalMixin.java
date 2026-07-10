package com.reggarf.mods.transferables.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.fluid.PortalFlowSource;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.PipeConnection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

@Mixin(value = PipeConnection.class, remap = false)
public abstract class PipePortalMixin {

    @Shadow public Direction side;
    @Shadow Optional<FlowSource> source;

    @Inject(method = "determineSource", at = @At("HEAD"), cancellable = true)
    private void transferables$portalSource(Level world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(world instanceof ServerLevel)) // server drives the transfer; client keeps vanilla determination
            return;
        if (!PortalProvider.isSupportedPortal(world.getBlockState(pos.relative(side))))
            return;
        // Bridge the portal-facing end. The source resolves its partner + shared buffer lazily,
        // so this also handles the far pipe being built afterwards.
        source = Optional.of(new PortalFlowSource(pos, side));
        cir.setReturnValue(true);
    }
}