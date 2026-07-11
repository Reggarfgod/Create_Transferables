package com.reggarf.mods.transferables.api;

import net.minecraft.core.Direction;

import javax.annotation.Nullable;

public interface PortalAccess {
	boolean create$isPortalSender();
	float create$currentSpeed();
	float create$exportableSU();
	float create$localDemand();

	// NEW METHODS FOR CLIENT SYNC
	boolean create$hasPortalConnection();
	@Nullable
    Direction create$getPortalDirection();
}
