package com.reggarf.mods.transferables.mixin.client;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.reggarf.mods.transferables.client.portal.kinetics.PortalShaftHalfInstances;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;

import dev.engine_room.flywheel.api.instance.Instance;
import net.minecraft.core.Direction;

/** Portal {@code SHAFT_HALF} for encased cogwheels (same instance type as their open-face stubs). */
@Mixin(value = EncasedCogVisual.class, remap = false)
public abstract class EncasedCogVisualMixin {

	@Unique @Nullable private RotatingInstance transferables$portalHalf;
	@Unique @Nullable private Direction transferables$towardPortal;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void transferables$initPortalHalf(CallbackInfo ci) {
		transferables$refreshPortalHalf();
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void transferables$updatePortalHalf(float pt, CallbackInfo ci) {
		transferables$refreshPortalHalf();
	}

	@Inject(method = "updateLight", at = @At("TAIL"))
	private void transferables$lightPortalHalf(float partialTick, CallbackInfo ci) {
		KineticBlockEntity be = transferables$be();
		if (transferables$portalHalf != null && transferables$towardPortal != null && be != null)
			transferables$access().transferables$relight(
					PortalShaftHalfInstances.portalPos(be, transferables$towardPortal), transferables$portalHalf);
	}

	@Inject(method = "_delete", at = @At("TAIL"))
	private void transferables$deletePortalHalf(CallbackInfo ci) {
		if (transferables$portalHalf != null) {
			transferables$portalHalf.delete();
			transferables$portalHalf = null;
			transferables$towardPortal = null;
		}
	}

	@Inject(method = "collectCrumblingInstances", at = @At("TAIL"))
	private void transferables$crumblePortalHalf(Consumer<Instance> consumer, CallbackInfo ci) {
		if (transferables$portalHalf != null)
			consumer.accept(transferables$portalHalf);
	}

	@Unique
	private AbstractBlockEntityVisualAccessor transferables$access() {
		return (AbstractBlockEntityVisualAccessor) this;
	}

	@Unique
	@Nullable
	private KineticBlockEntity transferables$be() {
		return transferables$access().transferables$blockEntity() instanceof KineticBlockEntity kbe ? kbe : null;
	}

	@Unique
	private void transferables$refreshPortalHalf() {
		KineticBlockEntity be = transferables$be();
		if (be == null)
			return;

		Direction towardPortal = PortalShaftHalfInstances.portalFacing(be);
		if (towardPortal == null) {
			if (transferables$portalHalf != null) {
				transferables$portalHalf.delete();
				transferables$portalHalf = null;
				transferables$towardPortal = null;
			}
			return;
		}

		AbstractBlockEntityVisualAccessor access = transferables$access();
		if (transferables$portalHalf == null || transferables$towardPortal != towardPortal) {
			if (transferables$portalHalf != null)
				transferables$portalHalf.delete();
			transferables$towardPortal = towardPortal;
			transferables$portalHalf = PortalShaftHalfInstances.create(
					((AbstractVisualAccessor) this).transferables$instancerProvider(), be, towardPortal);
			access.transferables$relight(PortalShaftHalfInstances.portalPos(be, towardPortal), transferables$portalHalf);
			return;
		}

		PortalShaftHalfInstances.sync(transferables$portalHalf, be, towardPortal);
	}
}
