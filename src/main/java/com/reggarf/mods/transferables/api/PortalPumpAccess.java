package com.reggarf.mods.transferables.api;

import com.reggarf.mods.transferables.portal.PortalShaftBinding;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/** Portal link state on a {@link com.simibubi.create.content.fluids.pump.PumpBlockEntity}. */
public interface PortalPumpAccess {
	boolean transferables$isPumpPortalConnected();

	@Nullable
	Direction transferables$getPumpPortalDirection();

	@Nullable
	PortalShaftBinding.Binding transferables$getPumpBoundPartner();

	void transferables$setPumpPortalConnection(Direction towardPortal, PortalShaftBinding.Binding partner);

	void transferables$setPumpPortalBinding(@Nullable PortalShaftBinding.Binding partner);

	void transferables$clearPumpPortalConnection();
}
