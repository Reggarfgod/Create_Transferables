package com.reggarf.mods.transferables.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reggarf.mods.transferables.api.PortalShaftProvider;
import com.reggarf.mods.transferables.network.PortalShaftLink;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "transferables", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalShaftClientEvents {
	private static final int HORIZONTAL_RENDER_RADIUS = 18;
	private static final int VERTICAL_RENDER_RADIUS = 10;
	private static final float HALF_SHAFT_LENGTH = 0.5f;

	private PortalShaftClientEvents() {}

	@SubscribeEvent
	public static void renderPortalShafts(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null)
			return;

		Camera camera = minecraft.gameRenderer.getMainCamera();
		BlockPos cameraPos = camera.getBlockPosition();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
		PoseStack poseStack = event.getPoseStack();
		VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());

		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		for (int x = -HORIZONTAL_RENDER_RADIUS; x <= HORIZONTAL_RENDER_RADIUS; x++) {
			for (int y = -VERTICAL_RENDER_RADIUS; y <= VERTICAL_RENDER_RADIUS; y++) {
				for (int z = -HORIZONTAL_RENDER_RADIUS; z <= HORIZONTAL_RENDER_RADIUS; z++) {
					mutable.set(cameraPos.getX() + x, cameraPos.getY() + y, cameraPos.getZ() + z);
					if (!PortalShaftProvider.isSupportedPortal(level.getBlockState(mutable)))
						continue;

					renderPortalBlockShafts(level, mutable.immutable(), camera, poseStack, bufferSource, buffer);
				}
			}
		}

		bufferSource.endBatch(RenderType.solid());
		bufferSource.endBatch(RenderType.cutout());
		bufferSource.endBatch(RenderType.cutoutMipped());
	}

	private static void renderPortalBlockShafts(ClientLevel level, BlockPos portalPos, Camera camera, PoseStack poseStack,
	                                            MultiBufferSource.BufferSource bufferSource, VertexConsumer buffer) {
		for (Direction fromPortalToShaft : Direction.values()) {
			Direction shaftTowardPortal = fromPortalToShaft.getOpposite();
			BlockPos shaftPos = portalPos.relative(fromPortalToShaft);
			BlockState shaftState = level.getBlockState(shaftPos);
			Axis axis = PortalShaftLink.getPortalShaftAxis(shaftState);
			if (axis == null || axis != shaftTowardPortal.getAxis())
				continue;

			BlockEntity blockEntity = level.getBlockEntity(shaftPos);
			if (!(blockEntity instanceof KineticBlockEntity shaft))
				continue;

			renderShaftOnPortalBlock(shaft, shaftState, level, portalPos, axis, shaftTowardPortal, camera, poseStack,
					bufferSource, buffer);
		}
	}

	private static void renderShaftOnPortalBlock(KineticBlockEntity shaft, BlockState shaftState, ClientLevel level,
	                                             BlockPos portalPos, Axis axis, Direction shaftTowardPortal, Camera camera, PoseStack poseStack,
	                                             MultiBufferSource.BufferSource bufferSource, VertexConsumer buffer) {
		poseStack.pushPose();
		poseStack.translate(
				portalPos.getX() - camera.getPosition().x,
				portalPos.getY() - camera.getPosition().y,
				portalPos.getZ() - camera.getPosition().z
		);
		renderNearestPortalHalf(shaftTowardPortal, poseStack);

		int light = LevelRenderer.getLightColor(level, portalPos);
		if (!(shaftState.getBlock() instanceof ShaftBlock)) {
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(shaftState, poseStack, bufferSource, light,
					OverlayTexture.NO_OVERLAY);
		}
		KineticBlockEntityRenderer.renderRotatingKineticBlock(shaft, KineticBlockEntityRenderer.shaft(axis), poseStack,
				buffer, light);
		poseStack.popPose();
	}

	private static void renderNearestPortalHalf(Direction shaftTowardPortal, PoseStack poseStack) {
		float length = HALF_SHAFT_LENGTH;
		float offset = shaftTowardPortal.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1f - length : 0f;

		switch (shaftTowardPortal.getAxis()) {
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
}
