package com.reggarf.mods.transferables.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.network.PortalShaftLink;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;


public final class PortalShaftClientEvents {
    private static final int HORIZONTAL_RENDER_RADIUS = 18;
    private static final int VERTICAL_RENDER_RADIUS = 10;
    private static final float HALF_SHAFT_LENGTH = 0.5f;
    private static final int MAX_LIGHT = 15728880;

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
        bufferSource.endBatch(RenderType.cutout());
        bufferSource.endBatch(RenderType.cutoutMipped());
        bufferSource.endBatch(RenderType.translucent());
    }

    private static void renderPortalBlockShafts(ClientLevel level, BlockPos portalPos, Camera camera,
                                                PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
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

            renderShaftOnPortalBlock(shaft, shaftState, level, portalPos, axis, shaftTowardPortal, camera, poseStack, bufferSource);
        }
    }

    private static void renderShaftOnPortalBlock(KineticBlockEntity shaft, BlockState shaftState, ClientLevel level,
                                                 BlockPos portalPos, Axis axis, Direction shaftTowardPortal, Camera camera,
                                                 PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.translate(
                portalPos.getX() - camera.getPosition().x,
                portalPos.getY() - camera.getPosition().y,
                portalPos.getZ() - camera.getPosition().z
        );

        int actualLight = LevelRenderer.getLightColor(level, portalPos);

        poseStack.pushPose();
        renderNearestPortalHalf(shaftTowardPortal, poseStack);

        MultiBufferSource solidTintedSource = rt -> new TintedVertexConsumer(bufferSource.getBuffer(rt), 170, 50, 255, 255);
        VertexConsumer solidBuffer = solidTintedSource.getBuffer(RenderType.cutoutMipped());

        if (!(shaftState.getBlock() instanceof ShaftBlock)) {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(shaftState, poseStack, solidTintedSource, actualLight,
                    OverlayTexture.NO_OVERLAY);
        }
        KineticBlockEntityRenderer.renderRotatingKineticBlock(shaft, KineticBlockEntityRenderer.shaft(axis), poseStack,
                solidBuffer, actualLight);
        poseStack.popPose();

        poseStack.pushPose();
        renderNearestPortalHalf(shaftTowardPortal, poseStack);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(1.2f, 1.2f, 1.2f);
        poseStack.translate(-0.5, -0.5, -0.5);
        VertexConsumer auraBuffer = bufferSource.getBuffer(RenderType.translucent());
        VertexConsumer tintedAuraBuffer = new TintedVertexConsumer(auraBuffer, 210, 100, 255, 80);
        KineticBlockEntityRenderer.renderRotatingKineticBlock(shaft, KineticBlockEntityRenderer.shaft(axis), poseStack,
                tintedAuraBuffer, MAX_LIGHT);

        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderNearestPortalHalf(Direction shaftTowardPortal, PoseStack poseStack) {
        float length = HALF_SHAFT_LENGTH;
        float offset = (shaftTowardPortal.getAxisDirection() == Direction.AxisDirection.POSITIVE) ? 0f : 1f - length;
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
    private static class TintedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int tintR, tintG, tintB, tintA;

        public TintedVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
            this.delegate = delegate;
            this.tintR = r;
            this.tintG = g;
            this.tintB = b;
            this.tintA = a;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color((r * tintR) / 255, (g * tintG) / 255, (b * tintB) / 255, (a * tintA) / 255);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor((r * tintR) / 255, (g * tintG) / 255, (b * tintB) / 255, (a * tintA) / 255);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}