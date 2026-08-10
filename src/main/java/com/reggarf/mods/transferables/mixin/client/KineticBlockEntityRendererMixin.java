package com.reggarf.mods.transferables.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.content.kinetics.portal.PortalExtensionMath;
import com.reggarf.mods.transferables.content.kinetics.portal.PortalShaftLink;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;

/** Clips the real kinetic block renderer when a shaft is portal-connected (Flywheel off). */
@Mixin(value = KineticBlockEntityRenderer.class, remap = false)
public abstract class KineticBlockEntityRendererMixin<T extends KineticBlockEntity> {

	@Unique
	private static final ThreadLocal<Boolean> transferables$portalClipActive = ThreadLocal.withInitial(() -> false);

	@Inject(method = "renderSafe", at = @At("HEAD"))
	private void transferables$beginPortalClip(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
	                                           int light, int overlay, CallbackInfo ci) {
		if (!(be instanceof PortalAccess access) || !access.transferables$isPortalConnected())
			return;
		if (PortalShaftLink.getPortalShaftAxis(be.getBlockState()) == null)
			return;
		Direction towardPortal = access.transferables$getPortalDirection();
		if (towardPortal == null)
			return;
		ms.pushPose();
		PortalExtensionMath.applyPortalClip(towardPortal, ms);
		transferables$portalClipActive.set(true);
	}

	@Inject(method = "renderSafe", at = @At("RETURN"))
	private void transferables$endPortalClip(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
	                                       int light, int overlay, CallbackInfo ci) {
		if (!transferables$portalClipActive.get())
			return;
		ms.popPose();
		transferables$portalClipActive.set(false);
	}
}
