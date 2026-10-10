package com.reggarf.mods.transferables.client.portal.fluids;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.content.portal.fluids.PortalFluidLink;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidRenderer;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import net.createmod.catnip.data.Iterate;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a visual pipe/pump extension into the portal block, linking the mechanical pump
 * into the portal similarly to Create's portal tracks.
 */
public final class PortalFluidRenderer {
	private static final int HORIZONTAL_RENDER_RADIUS = 18;
	private static final int VERTICAL_RENDER_RADIUS = 14;
	private static final float HALF_PIPE_LENGTH = 0.5f;
	private static final float PIPE_OVERLAP = 0.150f;
	private static final float FLUID_RADIUS = 3 / 16f;
	private static final float FLUID_PROGRESS = 0.2f;

	private PortalFluidRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(PortalFluidRenderer::renderPortalFluids);
	}

	private static void renderPortalFluids(WorldRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null)
			return;

		PoseStack poseStack = context.matrixStack();
		if (poseStack == null)
			return;

		Camera camera = context.camera();
		Vec3 cameraPos = camera.getPosition();
		BlockPos cameraBlock = camera.getBlockPosition();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		for (int x = -HORIZONTAL_RENDER_RADIUS; x <= HORIZONTAL_RENDER_RADIUS; x++) {
			for (int y = -VERTICAL_RENDER_RADIUS; y <= VERTICAL_RENDER_RADIUS; y++) {
				for (int z = -HORIZONTAL_RENDER_RADIUS; z <= HORIZONTAL_RENDER_RADIUS; z++) {
					mutable.set(cameraBlock.getX() + x, cameraBlock.getY() + y, cameraBlock.getZ() + z);
					if (!PortalProvider.isSupportedPortal(level.getBlockState(mutable)))
						continue;
					renderPortalBlockPipes(level, mutable.immutable(), cameraPos, poseStack, bufferSource);
				}
			}
		}

		bufferSource.endBatch(RenderType.solid());
		bufferSource.endBatch(RenderType.cutout());
		bufferSource.endBatch(RenderType.cutoutMipped());
		bufferSource.endBatch(RenderType.translucent());
	}

	private static void renderPortalBlockPipes(ClientLevel level, BlockPos portalPos, Vec3 cameraPos,
	                                           PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
		for (Direction fromPortalToPipe : Direction.values()) {
			Direction pipeTowardPortal = fromPortalToPipe.getOpposite();
			BlockPos pipePos = portalPos.relative(fromPortalToPipe);
			BlockState pipeState = level.getBlockState(pipePos);

			if (!PortalFluidLink.isPortalFluidNode(pipeState))
				continue;

			FluidTransportBehaviour pipe = BlockEntityBehaviour.get(level, pipePos, FluidTransportBehaviour.TYPE);
			if (pipe == null)
				continue;

			if (pipe instanceof PortalPumpAccess access) {
				boolean validFacing;
				if (access.transferables$isPumpPortalConnected()) {
					validFacing = access.transferables$getPumpPortalDirection() == pipeTowardPortal;
				} else {
					validFacing = PortalFluidLink.isFacingPortal(level, pipePos, pipeTowardPortal);
				}
				if (!validFacing)
					continue;
			} else if (!pipe.canHaveFlowToward(pipeState, pipeTowardPortal)) {
				continue;
			}

			renderPipeOnPortalBlock(pipe, pipeState, level, portalPos, pipeTowardPortal, cameraPos, poseStack,
					bufferSource);
		}
	}

	private static void renderPipeOnPortalBlock(FluidTransportBehaviour pipe, BlockState pipeState, ClientLevel level,
	                                            BlockPos portalPos, Direction pipeTowardPortal, Vec3 cameraPos,
	                                            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
		poseStack.pushPose();
		poseStack.translate(
				portalPos.getX() - cameraPos.x,
				portalPos.getY() - cameraPos.y,
				portalPos.getZ() - cameraPos.z);

		int light = LevelRenderer.getLightColor(level, portalPos);

		BlockState renderState = AllBlocks.FLUID_PIPE.getDefaultState();
		Direction.Axis axis = pipeTowardPortal.getAxis();

		if (renderState.hasProperty(BlockStateProperties.UP)) {
			renderState = renderState
					.setValue(BlockStateProperties.UP, axis == Direction.Axis.Y)
					.setValue(BlockStateProperties.DOWN, axis == Direction.Axis.Y)
					.setValue(BlockStateProperties.NORTH, axis == Direction.Axis.Z)
					.setValue(BlockStateProperties.SOUTH, axis == Direction.Axis.Z)
					.setValue(BlockStateProperties.EAST, axis == Direction.Axis.X)
					.setValue(BlockStateProperties.WEST, axis == Direction.Axis.X);
		} else {
			renderState = pipeState;
		}

		// 1) half-pipe stub continuing the pipe into the portal
		poseStack.pushPose();
		renderNearestPortalHalf(pipeTowardPortal, poseStack);
		Minecraft.getInstance()
				.getBlockRenderer()
				.renderSingleBlock(renderState, poseStack, bufferSource, light, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();

		// 2) fluid stream inside the stub
		FluidStack fluid = readPipeFluid(pipe, pipeTowardPortal);
		if (!fluid.isEmpty()) {
			poseStack.pushPose();
			FluidRenderer.renderFluidStream(fluid, pipeTowardPortal, FLUID_RADIUS, FLUID_PROGRESS, false,
					bufferSource, poseStack, light);
			poseStack.popPose();
		}

		poseStack.popPose();
	}

	private static void renderNearestPortalHalf(Direction pipeTowardPortal, PoseStack poseStack) {
		float length = HALF_PIPE_LENGTH;
		float scaleLen = length + PIPE_OVERLAP;
		float offset = (pipeTowardPortal.getAxisDirection() == Direction.AxisDirection.POSITIVE)
				? -PIPE_OVERLAP
				: 1f - length;

		switch (pipeTowardPortal.getAxis()) {
			case X -> {
				poseStack.translate(offset, 0, 0);
				poseStack.scale(scaleLen, 1, 1);
			}
			case Y -> {
				poseStack.translate(0, offset, 0);
				poseStack.scale(1, scaleLen, 1);
			}
			case Z -> {
				poseStack.translate(0, 0, offset);
				poseStack.scale(1, 1, scaleLen);
			}
		}
	}

	private static FluidStack readPipeFluid(FluidTransportBehaviour pipe, Direction towardPortal) {
		try {
			FluidStack outward = pipe.getProvidedOutwardFluid(towardPortal);
			if (outward != null && !outward.isEmpty())
				return outward;
			for (Direction d : Iterate.directions) {
				PipeConnection.Flow flow = pipe.getFlow(d);
				if (flow != null && flow.fluid != null && !flow.fluid.isEmpty())
					return flow.fluid;
			}
		} catch (Exception ignored) {}
		return FluidStack.EMPTY;
	}
}
