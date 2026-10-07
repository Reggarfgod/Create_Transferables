package com.reggarf.mods.transferables;

import com.reggarf.mods.transferables.client.portal.fluids.PortalFluidRenderer;
import com.reggarf.mods.transferables.client.portal.fluids.PortalPumpParticles;
import com.reggarf.mods.transferables.client.portal.kinetics.PortalShaftParticles;
import com.reggarf.mods.transferables.client.portal.kinetics.PortalShaftRenderer;

import net.fabricmc.api.ClientModInitializer;

public class TransferablesClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		PortalShaftRenderer.register();
		PortalFluidRenderer.register();
		PortalShaftParticles.register();
		PortalPumpParticles.register();
	}
}
