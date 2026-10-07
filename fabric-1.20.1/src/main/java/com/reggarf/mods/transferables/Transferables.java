package com.reggarf.mods.transferables;

import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.content.portal.PortalDropEvents;
import com.reggarf.mods.transferables.content.portal.fluids.PortalFluidEvents;
import com.reggarf.mods.transferables.content.portal.kinetics.PortalShaftEvents;

import net.fabricmc.api.ModInitializer;

public class Transferables implements ModInitializer {

	public static final String MODID = "transferables";

	@Override
	public void onInitialize() {
		PortalProvider.registerDefaults();
		PortalDropEvents.register();
		PortalShaftEvents.register();
		PortalFluidEvents.register();
	}
}
