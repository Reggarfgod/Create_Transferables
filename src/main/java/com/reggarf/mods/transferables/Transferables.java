package com.reggarf.mods.transferables;

import com.mojang.logging.LogUtils;
import com.reggarf.mods.transferables.client.portal.PortalFluidRenderer;
import com.reggarf.mods.transferables.client.portal.PortalPumpClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Transferables.MODID)
public class Transferables {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "transferables";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Transferables() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            event.enqueueWork(() -> {
                //MinecraftForge.EVENT_BUS.register(PortalFluidRenderer.class);
                MinecraftForge.EVENT_BUS.register(PortalPumpClient.class);
            });
        }
    }
}
