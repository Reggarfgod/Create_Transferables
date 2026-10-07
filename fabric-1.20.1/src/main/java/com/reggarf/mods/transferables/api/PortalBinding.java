package com.reggarf.mods.transferables.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Cross-dimension portal endpoint shared by shafts and pumps. */
public record PortalBinding(ResourceKey<Level> dimension, BlockPos pos) {}
