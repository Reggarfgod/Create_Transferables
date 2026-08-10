package com.reggarf.mods.transferables.api;

import com.reggarf.mods.transferables.portal.PortalShaftBinding;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Cross-dimension portal shaft state stored on {@link com.simibubi.create.content.kinetics.base.KineticBlockEntity}. */
public interface PortalAccess {
	boolean create$isPortalSender();

	float create$currentSpeed();

	float create$exportableSU();

	float create$localDemand();

	boolean transferables$isPortalConnected();

	@Nullable
	Direction transferables$getPortalDirection();

	@Nullable
	PortalShaftBinding.Binding transferables$getBoundPartner();

	void transferables$setPortalConnection(Direction towardPortal, PortalShaftBinding.Binding partner);

	void transferables$setPortalBinding(@Nullable PortalShaftBinding.Binding partner);

	void transferables$clearPortalConnection();

	default boolean transferables$hasPortalVisual() {
		return transferables$isPortalConnected();
	}

	@Nullable
	default PortalShaftBinding.Binding transferables$getPortalExit() {
		return transferables$getBoundPartner();
	}

	default boolean create$hasPortalConnection() {
		return transferables$isPortalConnected();
	}

	@Nullable
	default Direction create$getPortalDirection() {
		return transferables$getPortalDirection();
	}

	/** @deprecated use {@link #transferables$setPortalConnection} */
	@Deprecated
	default void transferables$setPortalVisual(@Nullable Direction towardPortal,
	                                           @Nullable com.reggarf.mods.transferables.portal.PortalExitBinding exit) {
		if (towardPortal == null || exit == null) {
			transferables$clearPortalConnection();
			return;
		}
		transferables$setPortalConnection(towardPortal,
				new PortalShaftBinding.Binding(exit.dimension(), exit.pos()));
	}
}
