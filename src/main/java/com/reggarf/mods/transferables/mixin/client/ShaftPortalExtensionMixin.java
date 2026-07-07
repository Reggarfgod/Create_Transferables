package com.reggarf.mods.transferables.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

@Mixin(value = KineticBlockEntityRenderer.class, remap = false)
public abstract class ShaftPortalExtensionMixin {
	@Unique
	private static final float transferables$HALF_SHAFT_LENGTH = 1f;

	@Inject(method = "renderSafe", at = @At("RETURN"))
	private void transferables$extendShaftIntoPortal(KineticBlockEntity shaft, float partialTicks, PoseStack poseStack,
		MultiBufferSource bufferSource, int light, int overlay, CallbackInfo ci) {
		Level level = shaft.getLevel();
		if (level == null)
			return;

		BlockState state = shaft.getBlockState();
		if (!(state.getBlock() instanceof ShaftBlock))
			return;

		Axis axis = state.getValue(BlockStateProperties.AXIS);
		transferables$renderExtension(shaft, level, axis,
			Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE), poseStack, bufferSource, light);
		transferables$renderExtension(shaft, level, axis,
			Direction.fromAxisAndDirection(axis, AxisDirection.NEGATIVE), poseStack, bufferSource, light);
	}

	@Unique
	private static void transferables$renderExtension(KineticBlockEntity shaft, Level level, Axis axis,
		Direction direction, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
		Block portalBlock = level.getBlockState(shaft.getBlockPos().relative(direction)).getBlock();
		if (!transferables$isPortalBlock(portalBlock))
			return;

		poseStack.pushPose();
		poseStack.translate(direction.getStepX(), direction.getStepY(), direction.getStepZ());
		transferables$renderNearestHalf(direction, poseStack);
		KineticBlockEntityRenderer.renderRotatingKineticBlock(shaft, KineticBlockEntityRenderer.shaft(axis), poseStack,
			bufferSource.getBuffer(RenderType.cutoutMipped()), light);
		poseStack.popPose();
	}

	@Unique
	private static void transferables$renderNearestHalf(Direction direction, PoseStack poseStack) {
		float length = transferables$HALF_SHAFT_LENGTH;
		float offset = direction.getAxisDirection() == AxisDirection.NEGATIVE ? 1f - length : 0f;

		switch (direction.getAxis()) {
		case X -> {
			poseStack.translate(offset, 0, 0);
			poseStack.scale(length, 1, 1);
		}
		case Y -> {
			poseStack.translate(0, offset, 0);
			poseStack.scale(1, length, 1);
		}
		case Z -> {
			poseStack.translate(0, 0, offset);
			poseStack.scale(1, 1, length);
		}
		}
	}

	@Unique
	private static boolean transferables$isPortalBlock(Block block) {
		return block == Blocks.NETHER_PORTAL || PortalTrackProvider.REGISTRY.get(block) != null;
	}
}
