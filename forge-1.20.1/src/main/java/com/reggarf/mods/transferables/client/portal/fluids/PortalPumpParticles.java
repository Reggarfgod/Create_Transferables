package com.reggarf.mods.transferables.client.portal.fluids;

import com.reggarf.mods.transferables.Transferables;
import com.reggarf.mods.transferables.api.PortalProvider;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.trains.CubeParticleData;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

/** Spawns subtle portal particles on bound pumps, matching {@link com.reggarf.mods.transferables.client.portal.kinetics.PortalShaftParticles}. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = Transferables.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalPumpParticles {
	private static final int PARTICLE_RADIUS = 16;
	private static int tickCounter;

	private PortalPumpParticles() {}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null)
			return;

		if (++tickCounter % 4 != 0)
			return;

		BlockPos center = minecraft.player.blockPosition();
		RandomSource random = level.random;
		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

		for (int x = -PARTICLE_RADIUS; x <= PARTICLE_RADIUS; x++) {
			for (int y = -8; y <= 8; y++) {
				for (int z = -PARTICLE_RADIUS; z <= PARTICLE_RADIUS; z++) {
					mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					BlockState state = level.getBlockState(mutable);
					if (!PumpBlock.isPump(state))
						continue;

					FluidTransportBehaviour pump = BlockEntityBehaviour.get(level, mutable, FluidTransportBehaviour.TYPE);
					if (pump == null || !(pump instanceof PortalPumpAccess access)
							|| !access.transferables$isPumpPortalConnected())
						continue;

					Direction towardPortal = access.transferables$getPumpPortalDirection();
					if (towardPortal == null)
						continue;
					if (!PortalProvider.isSupportedPortal(level.getBlockState(mutable.relative(towardPortal))))
						continue;

					double px = mutable.getX() + 0.5 + towardPortal.getStepX() * 0.45 + (random.nextDouble() - 0.5) * 0.2;
					double py = mutable.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
					double pz = mutable.getZ() + 0.5 + towardPortal.getStepZ() * 0.45 + (random.nextDouble() - 0.5) * 0.2;
					level.addParticle(new CubeParticleData(1, random.nextFloat(), 1,
							0.0125f + 0.0625f * random.nextFloat(), 30, false), px, py, pz, 0, 0.04, 0);
				}
			}
		}
	}
}
