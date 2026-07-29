package com.reggarf.mods.transferables;

import com.mojang.logging.LogUtils;

import com.reggarf.mods.transferables.client.PortalFluidClientEvents;


import com.reggarf.mods.transferables.client.PortalShaftClientEvents;
import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Transferables.MODID)
public class Transferables {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "transferables";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Transferables() {
        //IEventBus modEventBus = ModLoadingContext.get().getModEventBus();
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            event.enqueueWork(() -> {
                NeoForge.EVENT_BUS.register(PortalShaftClientEvents.class);
                NeoForge.EVENT_BUS.register(PortalFluidClientEvents.class);
            });

        }
    }
}