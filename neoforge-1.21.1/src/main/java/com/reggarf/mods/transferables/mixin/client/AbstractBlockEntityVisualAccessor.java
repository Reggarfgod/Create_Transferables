package com.reggarf.mods.transferables.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(value = AbstractBlockEntityVisual.class, remap = false)
public interface AbstractBlockEntityVisualAccessor {
	@Accessor("blockEntity")
	BlockEntity transferables$blockEntity();

	@Invoker("relight")
	void transferables$relight(BlockPos pos, FlatLit... instances);
}
