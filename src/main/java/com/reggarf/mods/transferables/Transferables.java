package com.reggarf.mods.transferables;

import com.reggarf.mods.transferables.api.PortalProvider;
import net.neoforged.fml.common.Mod;

@Mod(Transferables.MODID)
public class Transferables {

	public static final String MODID = "transferables";

	public Transferables() {
		PortalProvider.registerDefaults();
	}
}
