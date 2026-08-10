package com.reggarf.mods.transferables.portal;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;

/**
 * Clips kinetic models to a short half-shaft segment extending from the block face toward the portal.
 */
public final class PortalExtensionMath {
	public static final float PORTAL_STUB_LENGTH = 0.5f;

	private PortalExtensionMath() {}

	public static void applyPortalClip(Direction towardPortal, PoseStack poseStack) {
		float length = PORTAL_STUB_LENGTH;
		float offset = 1f - length;
		switch (towardPortal.getAxis()) {
			case X -> {
				if (towardPortal.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
					poseStack.translate(offset, 0, 0);
					poseStack.scale(length, 1, 1);
				} else {
					poseStack.scale(length, 1, 1);
				}
			}
			case Y -> {
				if (towardPortal.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
					poseStack.translate(0, offset, 0);
					poseStack.scale(1, length, 1);
				} else {
					poseStack.scale(1, length, 1);
				}
			}
			case Z -> {
				if (towardPortal.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
					poseStack.translate(0, 0, offset);
					poseStack.scale(1, 1, length);
				} else {
					poseStack.scale(1, 1, length);
				}
			}
		}
	}
}
