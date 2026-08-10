package com.reggarf.mods.transferables.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where a portal shaft emerges on the other side — mirrors Create's exit {@link net.createmod.catnip.math.BlockFace}
 * from {@code PortalTrackProvider.getOtherSide}.
 */
public record PortalExitBinding(ResourceKey<Level> dimension, BlockPos pos, Direction towardPortal) {}
