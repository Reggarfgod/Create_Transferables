package com.reggarf.mods.transferables.api;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;

/** Cross-dimension portal shaft state stored on Create kinetic block entities. */
public interface PortalAccess {
	boolean create$isPortalSender();

	float create$currentSpeed();

	float create$exportableSU();

	float create$localDemand();

	boolean transferables$isPortalConnected();

	@Nullable
	Direction transferables$getPortalDirection();

	@Nullable
	PortalBinding transferables$getBoundPartner();

	void transferables$setPortalConnection(Direction towardPortal, PortalBinding partner);

	void transferables$clearPortalConnection();

	boolean transferables$isPlayerPlaced();

	void transferables$setPlayerPlaced(boolean playerPlaced);
}
