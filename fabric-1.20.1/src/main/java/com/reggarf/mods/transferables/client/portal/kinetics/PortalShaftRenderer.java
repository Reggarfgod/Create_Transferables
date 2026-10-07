package com.reggarf.mods.transferables.client.portal.kinetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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
import net.minecraft.world.phys.Vec3;

/**
 * CPU fallback when Flywheel is off: renders Create's {@code SHAFT_HALF} into the portal
 * (same partial + kinetic angle motors use). With Flywheel, {@link com.reggarf.mods.transferables.mixin.client.SingleAxisRotatingVisualMixin}
 * registers the half-shaft as a real rotating instance instead.
 */
public final class PortalShaftRenderer {
	private static final int HORIZONTAL_RENDER_RADIUS = 18;
	private static final int VERTICAL_RENDER_RADIUS = 10;

	private PortalShaftRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PortalShaftRenderer::renderPortalShafts);
	}

	private static void renderPortalShafts(WorldRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || VisualizationManager.supportsVisualization(level))
			return;

		MultiBufferSource consumers = context.consumers();
		PoseStack poseStack = context.matrixStack();
		if (consumers == null || poseStack == null)
			return;

		Camera camera = context.camera();
		Vec3 cameraPos = camera.getPosition();
		BlockPos cameraBlock = camera.getBlockPosition();

		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		for (int x = -HORIZONTAL_RENDER_RADIUS; x <= HORIZONTAL_RENDER_RADIUS; x++) {
			for (int y = -VERTICAL_RENDER_RADIUS; y <= VERTICAL_RENDER_RADIUS; y++) {
				for (int z = -HORIZONTAL_RENDER_RADIUS; z <= HORIZONTAL_RENDER_RADIUS; z++) {
					mutable.set(cameraBlock.getX() + x, cameraBlock.getY() + y, cameraBlock.getZ() + z);
					if (!PortalProvider.isSupportedPortal(level.getBlockState(mutable)))
						continue;
					renderPortalBlockShafts(level, mutable.immutable(), cameraPos, poseStack, consumers);
				}
			}
		}

		if (consumers instanceof MultiBufferSource.BufferSource bufferSource)
			bufferSource.endBatch(RenderType.solid());
	}

	private static void renderPortalBlockShafts(ClientLevel level, BlockPos portalPos, Vec3 cameraPos,
	                                            PoseStack poseStack, MultiBufferSource consumers) {
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

			renderShaftHalf(shaft, shaftState, level, portalPos, towardPortal, cameraPos, poseStack, consumers);
		}
	}

	private static void renderShaftHalf(KineticBlockEntity shaft, BlockState shaftState, ClientLevel level,
	                                    BlockPos portalPos, Direction towardPortal, Vec3 cameraPos,
	                                    PoseStack poseStack, MultiBufferSource consumers) {
		Direction modelFacing = PortalShaftHalfInstances.modelFacing(towardPortal);
		Axis axis = ((IRotate) shaftState.getBlock()).getRotationAxis(shaftState);

		poseStack.pushPose();
		poseStack.translate(
				portalPos.getX() - cameraPos.x,
				portalPos.getY() - cameraPos.y,
				portalPos.getZ() - cameraPos.z);

		int light = LevelRenderer.getLightColor(level, portalPos);
		float angle = KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), axis);
		VertexConsumer solid = consumers.getBuffer(RenderType.solid());

		SuperByteBuffer half = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, shaftState, modelFacing);
		KineticBlockEntityRenderer.kineticRotationTransform(half, shaft, axis, angle, light).renderInto(poseStack, solid);

		poseStack.popPose();
	}
}
