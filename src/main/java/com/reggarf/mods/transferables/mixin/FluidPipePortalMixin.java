package com.reggarf.mods.transferables.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.network.PortalFluidLink;
import com.reggarf.mods.transferables.portal.PortalFluidBinding;
import com.reggarf.mods.transferables.portal.PortalShaftBinding;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

@Mixin(value = FluidTransportBehaviour.class, remap = false)
public abstract class FluidPipePortalMixin implements PortalPumpAccess {

	@Unique private int transferables$pumpCooldown = 0;
	@Unique private boolean transferables$pumpPortalConnected = false;
	@Unique @Nullable private Direction transferables$pumpPortalDirection = null;
	@Unique @Nullable private PortalShaftBinding.Binding transferables$pumpBoundPartner = null;

	@Inject(method = "tick", at = @At("TAIL"))
	private void transferables$portalPumpTick(CallbackInfo ci) {
		FluidTransportBehaviour self = (FluidTransportBehaviour) (Object) this;
		if (!(self.getWorld() instanceof ServerLevel serverLevel))
			return;
		if (!PortalFluidLink.isPortalPump(self.blockEntity.getBlockState()))
			return;
		if (--transferables$pumpCooldown > 0)
			return;
		transferables$pumpCooldown = 20;
		PortalFluidBinding.connectToPortal(self, serverLevel);
	}

	@Inject(method = "read", at = @At("TAIL"))
	private void transferables$readPortalPump(CompoundTag nbt, boolean clientPacket, CallbackInfo ci) {
		if (!clientPacket)
			return;
		transferables$pumpPortalConnected = nbt.getBoolean("PumpPortalConnected");
		transferables$pumpPortalDirection = nbt.contains("PumpPortalDir")
				? Direction.byName(nbt.getString("PumpPortalDir"))
				: null;
		if (nbt.contains("PumpBoundDimension") && nbt.contains("PumpBoundPos")) {
			ResourceKey<Level> dimension = ResourceKey.create(
					net.minecraft.core.registries.Registries.DIMENSION,
					ResourceLocation.parse(nbt.getString("PumpBoundDimension")));
			transferables$pumpBoundPartner = new PortalShaftBinding.Binding(dimension,
					BlockPos.of(nbt.getLong("PumpBoundPos")));
		} else {
			transferables$pumpBoundPartner = null;
		}
	}

	@Inject(method = "write", at = @At("TAIL"))
	private void transferables$writePortalPump(CompoundTag nbt, boolean clientPacket, CallbackInfo ci) {
		if (!clientPacket)
			return;
		nbt.putBoolean("PumpPortalConnected", transferables$pumpPortalConnected);
		if (transferables$pumpPortalDirection != null)
			nbt.putString("PumpPortalDir", transferables$pumpPortalDirection.getName());
		if (transferables$pumpBoundPartner != null) {
			nbt.putString("PumpBoundDimension", transferables$pumpBoundPartner.dimension().location().toString());
			nbt.putLong("PumpBoundPos", transferables$pumpBoundPartner.pos().asLong());
		}
	}

	@Override
	public boolean transferables$isPumpPortalConnected() {
		return transferables$pumpPortalConnected;
	}

	@Override
	@Nullable
	public Direction transferables$getPumpPortalDirection() {
		return transferables$pumpPortalDirection;
	}

	@Override
	@Nullable
	public PortalShaftBinding.Binding transferables$getPumpBoundPartner() {
		return transferables$pumpBoundPartner;
	}

	@Override
	public void transferables$setPumpPortalConnection(Direction towardPortal, PortalShaftBinding.Binding partner) {
		transferables$pumpPortalDirection = towardPortal;
		transferables$pumpBoundPartner = partner;
		transferables$pumpPortalConnected = towardPortal != null && partner != null;
	}

	@Override
	public void transferables$setPumpPortalBinding(@Nullable PortalShaftBinding.Binding partner) {
		transferables$pumpBoundPartner = partner;
		transferables$pumpPortalConnected = transferables$pumpPortalConnected && partner != null;
	}

	@Override
	public void transferables$clearPumpPortalConnection() {
		transferables$pumpPortalConnected = false;
		transferables$pumpPortalDirection = null;
		transferables$pumpBoundPartner = null;
	}
}
