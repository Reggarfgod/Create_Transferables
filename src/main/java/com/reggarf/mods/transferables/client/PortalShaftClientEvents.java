//package com.reggarf.mods.transferables.client;
//
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.mojang.blaze3d.vertex.VertexConsumer;
//import com.simibubi.create.api.contraption.train.PortalTrackProvider;
//import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
//import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
//import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
//
//import net.minecraft.client.Camera;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.multiplayer.ClientLevel;
//import net.minecraft.client.renderer.LevelRenderer;
//import net.minecraft.client.renderer.MultiBufferSource;
//import net.minecraft.client.renderer.RenderType;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.Direction;
//import net.minecraft.core.Direction.Axis;
//import net.minecraft.core.Direction.AxisDirection;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.level.block.entity.BlockEntity;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//import net.minecraftforge.api.distmarker.Dist;
//import net.minecraftforge.client.event.RenderLevelStageEvent;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//import net.minecraftforge.fml.common.Mod;
//
//@Mod.EventBusSubscriber(modid = "transferables", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
//public final class PortalShaftClientEvents {
//	private static final int HORIZONTAL_RENDER_RADIUS = 18;
//	private static final int VERTICAL_RENDER_RADIUS = 10;
//
//	private PortalShaftClientEvents() {}
//
//	@SubscribeEvent
//	public static void renderPortalShafts(RenderLevelStageEvent event) {
//		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
//			return;
//
//		Minecraft minecraft = Minecraft.getInstance();
//		ClientLevel level = minecraft.level;
//		if (level == null)
//			return;
//
//		Camera camera = minecraft.gameRenderer.getMainCamera();
//		BlockPos cameraPos = camera.getBlockPosition();
//		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
//		PoseStack poseStack = event.getPoseStack();
//		VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
//
//		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
//		for (int x = -HORIZONTAL_RENDER_RADIUS; x <= HORIZONTAL_RENDER_RADIUS; x++) {
//			for (int y = -VERTICAL_RENDER_RADIUS; y <= VERTICAL_RENDER_RADIUS; y++) {
//				for (int z = -HORIZONTAL_RENDER_RADIUS; z <= HORIZONTAL_RENDER_RADIUS; z++) {
//					mutable.set(cameraPos.getX() + x, cameraPos.getY() + y, cameraPos.getZ() + z);
//					BlockState state = level.getBlockState(mutable);
//					if (!(state.getBlock() instanceof ShaftBlock))
//						continue;
//
//					BlockEntity blockEntity = level.getBlockEntity(mutable);
//					if (!(blockEntity instanceof KineticBlockEntity shaft))
//						continue;
//
//					if (shaft.getSpeed() == 0)
//						continue;
//
//					Axis axis = state.getValue(BlockStateProperties.AXIS);
//					renderPortalEnd(shaft, level, axis, Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE),
//						camera, poseStack, buffer);
//					renderPortalEnd(shaft, level, axis, Direction.fromAxisAndDirection(axis, AxisDirection.NEGATIVE),
//						camera, poseStack, buffer);
//				}
//			}
//		}
//
//		bufferSource.endBatch(RenderType.cutoutMipped());
//	}
//
//	private static void renderPortalEnd(KineticBlockEntity shaft, ClientLevel level, Axis axis, Direction direction,
//		Camera camera, PoseStack poseStack, VertexConsumer buffer) {
//		BlockPos portalPos = shaft.getBlockPos().relative(direction);
//		Block portalBlock = level.getBlockState(portalPos).getBlock();
//		if (!isPortalBlock(portalBlock))
//			return;
//
//		poseStack.pushPose();
//		poseStack.translate(
//			portalPos.getX() - camera.getPosition().x,
//			portalPos.getY() - camera.getPosition().y,
//			portalPos.getZ() - camera.getPosition().z
//		);
//
//		int light = LevelRenderer.getLightColor(level, portalPos);
//		KineticBlockEntityRenderer.renderRotatingKineticBlock(shaft, KineticBlockEntityRenderer.shaft(axis), poseStack,
//			buffer, light);
//		poseStack.popPose();
//	}
//
//	private static boolean isPortalBlock(Block block) {
//		return block == Blocks.NETHER_PORTAL || PortalTrackProvider.REGISTRY.get(block) != null;
//	}
//}
