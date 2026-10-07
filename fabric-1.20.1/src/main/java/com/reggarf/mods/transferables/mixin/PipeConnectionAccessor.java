package com.reggarf.mods.transferables.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.PipeConnection;

@Mixin(value = PipeConnection.class, remap = false)
public interface PipeConnectionAccessor {
	@Accessor("source")
	Optional<FlowSource> transferables$getSource();
}
