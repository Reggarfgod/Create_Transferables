package com.reggarf.mods.transferables.client.portal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.network.PortalFluidLink;
import com.reggarf.mods.transferables.portal.PortalExtensionMath;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidRenderer;

import net.createmod.catnip.data.Iterate;
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
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;

public final class PortalFluidRenderer {
	private static final int HORIZONTAL_RENDER_RADIUS = 18;
	private static final int VERTICAL_RENDER_RADIUS = 14;
	private static final float FLUID_RADIUS = 3 / 16f;
	private static final float FLUID_PROGRESS = 0.2f;

	private PortalFluidRenderer() {}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
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

		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		for (int x = -HORIZONTAL_RENDER_RADIUS; x <= HORIZONTAL_RENDER_RADIUS; x++) {
			for (int y = -VERTICAL_RENDER_RADIUS; y <= VERTICAL_RENDER_RADIUS; y++) {
				for (int z = -HORIZONTAL_RENDER_RADIUS; z <= HORIZONTAL_RENDER_RADIUS; z++) {
					mutable.set(cameraPos.getX() + x, cameraPos.getY() + y, cameraPos.getZ() + z);
					BlockState state = level.getBlockState(mutable);
					if (!PortalFluidLink.isPortalPump(state))
						continue;

					FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, mutable,
							FluidTransportBehaviour.TYPE);
					if (!(pump instanceof PortalPumpAccess access) || !access.transferables$isPumpPortalConnected())
						continue;

					Direction towardPortal = access.transferables$getPumpPortalDirection();
					if (towardPortal == null)
						continue;
					if (!PortalProvider.isSupportedPortal(level.getBlockState(mutable.relative(towardPortal))))
						continue;

					renderPumpExtension(pump, level, mutable.immutable(), state, towardPortal, camera, poseStack,
							bufferSource);
				}
			}
		}

		bufferSource.endBatch(RenderType.solid());
		bufferSource.endBatch(RenderType.cutout());
		bufferSource.endBatch(RenderType.cutoutMipped());
		bufferSource.endBatch(RenderType.translucent());
	}

	private static void renderPumpExtension(FluidTransportBehaviour pump, ClientLevel level, BlockPos pumpPos,
	                                        BlockState pumpState, Direction towardPortal, Camera camera,
	                                        PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
		int light = LevelRenderer.getLightColor(level, pumpPos.relative(towardPortal));

		poseStack.pushPose();
		poseStack.translate(
				pumpPos.getX() - camera.getPosition().x,
				pumpPos.getY() - camera.getPosition().y,
				pumpPos.getZ() - camera.getPosition().z
		);

		poseStack.pushPose();
		PortalExtensionMath.applyPortalClip(towardPortal, poseStack);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(pumpState, poseStack, bufferSource, light,
				OverlayTexture.NO_OVERLAY);
		poseStack.popPose();

		FluidStack fluid = readPumpFluid(pump, towardPortal);
		if (!fluid.isEmpty()) {
			poseStack.pushPose();
			PortalExtensionMath.applyPortalClip(towardPortal, poseStack);
			FluidRenderer.renderFluidStream(fluid, towardPortal, FLUID_RADIUS, FLUID_PROGRESS, false, bufferSource,
					poseStack, light);
			poseStack.popPose();
		}

		poseStack.popPose();
	}

	private static FluidStack readPumpFluid(FluidTransportBehaviour pump, Direction towardPortal) {
		try {
			FluidStack outward = pump.getProvidedOutwardFluid(towardPortal);
			if (outward != null && !outward.isEmpty())
				return outward;
			for (Direction d : Iterate.directions) {
				PipeConnection.Flow flow = pump.getFlow(d);
				if (flow != null && flow.fluid != null && !flow.fluid.isEmpty())
					return flow.fluid;
			}
		} catch (Exception ignored) {
		}
		return FluidStack.EMPTY;
	}
}
