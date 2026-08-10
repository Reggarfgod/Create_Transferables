package com.reggarf.mods.transferables.api;

import com.reggarf.mods.transferables.content.kinetics.portal.PortalShaftBinding;
import net.minecraft.core.Direction;

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
}
