package com.reggarf.mods.transferables;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(Transferables.MODID)
public class Transferables {

	public static final String MODID = "transferables";

	public Transferables() {
		MinecraftForge.EVENT_BUS.register(this);
	}
}
