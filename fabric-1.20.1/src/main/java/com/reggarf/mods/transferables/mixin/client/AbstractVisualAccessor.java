package com.reggarf.mods.transferables.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;

@Mixin(value = AbstractVisual.class, remap = false)
public interface AbstractVisualAccessor {
	@Invoker("instancerProvider")
	InstancerProvider transferables$instancerProvider();
}
