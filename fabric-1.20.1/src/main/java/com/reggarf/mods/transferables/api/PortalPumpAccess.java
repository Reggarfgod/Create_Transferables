package com.reggarf.mods.transferables.api;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;

/** Portal link state on a mechanical pump's fluid transport behaviour. */
public interface PortalPumpAccess {
	boolean transferables$isPumpPortalConnected();

	@Nullable
	Direction transferables$getPumpPortalDirection();

	@Nullable
	PortalBinding transferables$getPumpBoundPartner();

	void transferables$setPumpPortalConnection(Direction towardPortal, PortalBinding partner);

	void transferables$clearPumpPortalConnection();

	boolean transferables$isPlayerPlaced();

	void transferables$setPlayerPlaced(boolean playerPlaced);
}
