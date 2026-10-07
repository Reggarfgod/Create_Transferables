package com.reggarf.mods.transferables.client.portal.kinetics;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.content.portal.kinetics.PortalShaftLink;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.trains.CubeParticleData;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Spawns subtle portal particles on bound shafts, matching Create's portal track {@code animateTick}. */
public final class PortalShaftParticles {
	private static final int PARTICLE_RADIUS = 16;
	private static int tickCounter;

	private PortalShaftParticles() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
	}

	private static void onClientTick() {
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
					if (PortalShaftLink.getPortalShaftAxis(state) == null)
						continue;

					if (!(level.getBlockEntity(mutable) instanceof KineticBlockEntity shaft))
						continue;
					if (!(shaft instanceof PortalAccess access) || !access.transferables$isPortalConnected())
						continue;

					Direction towardPortal = access.transferables$getPortalDirection();
					if (towardPortal == null)
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
