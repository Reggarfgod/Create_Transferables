package com.reggarf.mods.transferables.client.portal.kinetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reggarf.mods.transferables.Transferables;
import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.content.portal.kinetics.PortalShaftLink;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * CPU fallback when Flywheel is off: renders Create's {@code SHAFT_HALF} into the portal
 * (same partial + kinetic angle motors use). With Flywheel, {@link com.reggarf.mods.transferables.mixin.client.SingleAxisRotatingVisualMixin}
 * registers the half-shaft as a real rotating instance instead.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = Transferables.MODID)
public final class PortalShaftRenderer {
	private static final int HORIZONTAL_RENDER_RADIUS = 18;
	private static final int VERTICAL_RENDER_RADIUS = 10;

	private PortalShaftRenderer() {}

	@SubscribeEvent
	public static void renderPortalShafts(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || VisualizationManager.supportsVisualization(level))
			return;

		Camera camera = minecraft.gameRenderer.getMainCamera();
		BlockPos cameraPos = camera.getBlockPosition();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
		PoseStack poseStack = event.getPoseStack();

		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		for (int x = -HORIZONTAL_RENDER_RADIUS; x <= HORIZONTAL_RENDER_RADIUS; x++) {
			for (int y = -VERTICAL_RENDER_RADIUS; y <= VERTICAL_RENDER_RADIUS; y++) {
				for (int z = -HORIZONTAL_RENDER_RADIUS; z <= HORIZONTAL_RENDER_RADIUS; z++) {
					mutable.set(cameraPos.getX() + x, cameraPos.getY() + y, cameraPos.getZ() + z);
					if (!PortalProvider.isSupportedPortal(level.getBlockState(mutable)))
						continue;
					renderPortalBlockShafts(level, mutable.immutable(), camera, poseStack, bufferSource);
				}
			}
		}

		bufferSource.endBatch(RenderType.solid());
	}

	private static void renderPortalBlockShafts(ClientLevel level, BlockPos portalPos, Camera camera,
	                                            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
		for (Direction fromPortalToShaft : Direction.values()) {
			Direction towardPortal = fromPortalToShaft.getOpposite();
			BlockPos shaftPos = portalPos.relative(fromPortalToShaft);
			BlockState shaftState = level.getBlockState(shaftPos);
			Axis axis = PortalShaftLink.getPortalShaftAxis(shaftState);
			if (axis == null || axis != towardPortal.getAxis())
				continue;

			BlockEntity blockEntity = level.getBlockEntity(shaftPos);
			if (!(blockEntity instanceof KineticBlockEntity shaft))
				continue;

			if (shaft instanceof PortalAccess access && !access.transferables$isPortalConnected()) {
				if (!PortalShaftLink.isShaftFacingPortal(level, shaftPos, towardPortal))
					continue;
			}

			renderShaftHalf(shaft, shaftState, level, portalPos, towardPortal, camera, poseStack, bufferSource);
		}
	}

	private static void renderShaftHalf(KineticBlockEntity shaft, BlockState shaftState, ClientLevel level,
	                                    BlockPos portalPos, Direction towardPortal, Camera camera,
	                                    PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
		Direction modelFacing = PortalShaftHalfInstances.modelFacing(towardPortal);
		Axis axis = ((IRotate) shaftState.getBlock()).getRotationAxis(shaftState);

		poseStack.pushPose();
		poseStack.translate(
				portalPos.getX() - camera.getPosition().x,
				portalPos.getY() - camera.getPosition().y,
				portalPos.getZ() - camera.getPosition().z);

		int light = LevelRenderer.getLightColor(level, portalPos);
		float angle = KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), axis);
		VertexConsumer solid = bufferSource.getBuffer(RenderType.solid());

		SuperByteBuffer half = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, shaftState, modelFacing);
		KineticBlockEntityRenderer.kineticRotationTransform(half, shaft, axis, angle, light).renderInto(poseStack, solid);

		poseStack.popPose();
	}
}
