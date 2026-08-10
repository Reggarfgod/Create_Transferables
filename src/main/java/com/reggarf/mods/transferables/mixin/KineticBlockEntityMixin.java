package com.reggarf.mods.transferables.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.content.kinetics.portal.PortalShaftBinding;
import com.reggarf.mods.transferables.content.kinetics.portal.PortalShaftLink;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticBlockEntityMixin implements PortalAccess {

	@Shadow @Nullable public Long network;
	@Shadow @Nullable public BlockPos source;
	@Shadow protected float capacity;
	@Shadow protected float stress;
	@Shadow protected float lastStressApplied;
	@Shadow protected float lastCapacityProvided;

	@Shadow public abstract float getSpeed();
	@Shadow public abstract float getTheoreticalSpeed();
	@Shadow public abstract void setSpeed(float speed);
	@Shadow public abstract void setNetwork(@Nullable Long networkIn);
	@Shadow public abstract void onSpeedChanged(float previousSpeed);
	@Shadow public abstract void attachKinetics();
	@Shadow public abstract void detachKinetics();

	@Unique
	private static final float transferables$EPSILON = 1.0e-3f;

	@Unique
	private KineticBlockEntity transferables$self() {
		return (KineticBlockEntity) (Object) this;
	}

	@Unique @Nullable private ServerLevel transferables$otherLevel;
	@Unique @Nullable private BlockPos transferables$otherPos;
	@Unique private int transferables$cooldown = 0;
	@Unique private boolean transferables$receiver = false;
	@Unique private boolean transferables$sender = false;
	@Unique private float transferables$provided = 0f;
	@Unique private float transferables$lastGenerated = 0f;
	@Unique private float transferables$lastImportedCapacitySU = 0f;
	@Unique private float transferables$lastExportedStressSU = 0f;
	@Unique private float transferables$detachingGeneratedSpeed = 0f;

	@Unique private boolean transferables$portalConnected = false;
	@Unique @Nullable private Direction transferables$portalDirection = null;
	@Unique @Nullable private PortalShaftBinding.Binding transferables$boundPartner = null;

	@Inject(method = "tick", at = @At("TAIL"))
	private void transferables$portalTick(CallbackInfo ci) {
		KineticBlockEntity self = transferables$self();
		if (!(self.getLevel() instanceof ServerLevel serverLevel))
			return;

		BlockState state = self.getBlockState();
		if (PortalShaftLink.getPortalShaftAxis(state) == null)
			return;

		if (--transferables$cooldown <= 0) {
			transferables$cooldown = 20;
			PortalShaftBinding.connectToPortal(self, serverLevel);
			transferables$resolveKineticLink(serverLevel);
		}

		PortalAccess partner = transferables$partner();
		transferables$sender = transferables$computeSender();
		boolean nextReceiver = partner != null && partner.create$isPortalSender() && !transferables$sender;

		float gen = nextReceiver && partner != null ? partner.create$currentSpeed() : 0f;
		if (transferables$changed(gen, transferables$lastGenerated) || nextReceiver != transferables$receiver) {
			float previousGenerated = transferables$lastGenerated;
			transferables$applyGeneratedSpeed(gen, nextReceiver, previousGenerated);
			transferables$lastGenerated = gen;
		} else {
			transferables$receiver = nextReceiver;
		}

		transferables$syncStressTransfer(partner);
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void transferables$onRemove(CallbackInfo ci) {
		KineticBlockEntity self = transferables$self();
		Level level = self.getLevel();
		if (level instanceof ServerLevel serverLevel)
			PortalShaftBinding.onRemoved(self, this, serverLevel);
	}

	@Inject(method = "write", at = @At("TAIL"))
	private void transferables$writePortal(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
		if (!clientPacket)
			return;
		compound.putBoolean("PortalConnected", transferables$portalConnected);
		if (transferables$portalDirection != null)
			compound.putString("PortalDir", transferables$portalDirection.getName());
		if (transferables$boundPartner != null) {
			compound.putString("BoundDimension", transferables$boundPartner.dimension().location().toString());
			compound.putLong("BoundPos", transferables$boundPartner.pos().asLong());
		}
	}

	@Inject(method = "read", at = @At("TAIL"))
	private void transferables$readPortal(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
		if (!clientPacket)
			return;
		transferables$portalConnected = compound.getBoolean("PortalConnected");
		transferables$portalDirection = compound.contains("PortalDir")
				? Direction.byName(compound.getString("PortalDir"))
				: null;
		if (compound.contains("BoundDimension") && compound.contains("BoundPos")) {
			ResourceKey<Level> dimension = ResourceKey.create(
					net.minecraft.core.registries.Registries.DIMENSION,
					ResourceLocation.parse(compound.getString("BoundDimension")));
			transferables$boundPartner = new PortalShaftBinding.Binding(dimension, BlockPos.of(compound.getLong("BoundPos")));
		} else {
			transferables$boundPartner = null;
		}
	}

	@Unique
	private void transferables$resolveKineticLink(ServerLevel serverLevel) {
		transferables$otherLevel = null;
		transferables$otherPos = null;
		if (transferables$boundPartner == null)
			return;
		ServerLevel otherLevel = serverLevel.getServer().getLevel(transferables$boundPartner.dimension());
		if (otherLevel == null || !otherLevel.isLoaded(transferables$boundPartner.pos()))
			return;
		transferables$otherLevel = otherLevel;
		transferables$otherPos = transferables$boundPartner.pos();
	}

	@Unique @Nullable
	private PortalAccess transferables$partner() {
		if (transferables$otherLevel == null || transferables$otherPos == null)
			return null;
		if (!transferables$otherLevel.isLoaded(transferables$otherPos))
			return null;
		return transferables$otherLevel.getBlockEntity(transferables$otherPos) instanceof PortalAccess a ? a : null;
	}

	@Unique
	private boolean transferables$computeSender() {
		if (!transferables$portalConnected || network == null || getSpeed() == 0f)
			return false;
		return capacity - transferables$provided > transferables$EPSILON;
	}

	@Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
	private void transferables$genSpeed(CallbackInfoReturnable<Float> cir) {
		if (transferables$detachingGeneratedSpeed != 0f) {
			cir.setReturnValue(transferables$detachingGeneratedSpeed);
			return;
		}
		if (!transferables$receiver)
			return;
		PortalAccess partner = transferables$partner();
		cir.setReturnValue(partner == null ? 0f : partner.create$currentSpeed());
	}

	@Unique
	private void transferables$applyGeneratedSpeed(float gen, boolean nextReceiver, float previousGenerated) {
		KineticBlockEntity self = transferables$self();
		float prev = getSpeed();

		transferables$detachingGeneratedSpeed = transferables$receiver ? previousGenerated : 0f;
		detachKinetics();
		transferables$detachingGeneratedSpeed = 0f;

		transferables$receiver = nextReceiver;
		this.source = null;
		setSpeed(gen);
		if (!nextReceiver) {
			transferables$provided = 0f;
			transferables$lastImportedCapacitySU = 0f;
			this.lastCapacityProvided = 0f;
		}

		if (nextReceiver && gen != 0f)
			setNetwork(self.getBlockPos().asLong());
		else
			setNetwork(null);

		onSpeedChanged(prev);
		attachKinetics();
		self.sendData();
	}

	@Unique
	private void transferables$syncStressTransfer(@Nullable PortalAccess partner) {
		KineticBlockEntity self = transferables$self();
		if (!transferables$portalConnected || network == null) {
			transferables$provided = 0f;
			transferables$lastImportedCapacitySU = 0f;
			transferables$lastExportedStressSU = 0f;
			return;
		}

		float importedCapacitySU = transferables$receiver && partner != null ? partner.create$exportableSU() : 0f;
		if (transferables$changed(importedCapacitySU, transferables$lastImportedCapacitySU)) {
			transferables$lastImportedCapacitySU = importedCapacitySU;
			transferables$provided = importedCapacitySU;
			if (transferables$receiver) {
				float baseCapacity = transferables$toBase(importedCapacitySU, self.getGeneratedSpeed());
				this.lastCapacityProvided = baseCapacity;
				self.getOrCreateNetwork().updateCapacityFor(self, baseCapacity);
			}
			self.sendData();
		}

		float exportedStressSU = transferables$sender && !transferables$receiver && partner != null ? partner.create$localDemand() : 0f;
		if (transferables$changed(exportedStressSU, transferables$lastExportedStressSU)) {
			transferables$lastExportedStressSU = exportedStressSU;
			float baseStress = transferables$toBase(exportedStressSU, self.getTheoreticalSpeed());
			this.lastStressApplied = baseStress;
			self.getOrCreateNetwork().updateStressFor(self, baseStress);
			self.sendData();
		}
	}

	@Unique
	private float transferables$toBase(float actualSU, float speed) {
		float absSpeed = Math.abs(speed);
		if (actualSU <= transferables$EPSILON || absSpeed <= transferables$EPSILON)
			return 0f;
		return actualSU / absSpeed;
	}

	@Unique
	private boolean transferables$changed(float a, float b) {
		return Math.abs(a - b) > transferables$EPSILON;
	}

	@Override
	public boolean create$isPortalSender() {
		return transferables$sender;
	}

	@Override
	public float create$currentSpeed() {
		return getSpeed();
	}

	@Override
	public float create$exportableSU() {
		if (network == null)
			return 0f;
		float portalStress = Math.abs(lastStressApplied * transferables$self().getTheoreticalSpeed());
		float localStress = Math.max(0f, stress - portalStress);
		return Math.max(0f, capacity - localStress);
	}

	@Override
	public float create$localDemand() {
		if (network == null)
			return 0f;
		float portalStress = Math.abs(lastStressApplied * transferables$self().getTheoreticalSpeed());
		return Math.max(0f, stress - portalStress);
	}

	@Override
	public boolean transferables$isPortalConnected() {
		return transferables$portalConnected;
	}

	@Override
	@Nullable
	public Direction transferables$getPortalDirection() {
		return transferables$portalDirection;
	}

	@Override
	@Nullable
	public PortalShaftBinding.Binding transferables$getBoundPartner() {
		return transferables$boundPartner;
	}

	@Override
	public void transferables$setPortalConnection(Direction towardPortal, PortalShaftBinding.Binding partner) {
		transferables$portalDirection = towardPortal;
		transferables$boundPartner = partner;
		transferables$portalConnected = towardPortal != null && partner != null;
	}

	@Override
	public void transferables$setPortalBinding(@Nullable PortalShaftBinding.Binding partner) {
		transferables$boundPartner = partner;
		transferables$portalConnected = transferables$portalConnected && partner != null;
	}

	@Override
	public void transferables$clearPortalConnection() {
		transferables$portalConnected = false;
		transferables$portalDirection = null;
		transferables$boundPartner = null;
	}

	@Inject(method = "calculateAddedStressCapacity", at = @At("HEAD"), cancellable = true)
	private void transferables$addCapacity(CallbackInfoReturnable<Float> cir) {
		if (!transferables$receiver)
			return;
		PortalAccess partner = transferables$partner();
		float capSU = partner == null ? 0f : partner.create$exportableSU();
		transferables$provided = capSU;
		transferables$lastImportedCapacitySU = capSU;
		float baseCapacity = transferables$toBase(capSU, transferables$self().getGeneratedSpeed());
		this.lastCapacityProvided = baseCapacity;
		cir.setReturnValue(baseCapacity);
	}

	@Inject(method = "calculateStressApplied", at = @At("HEAD"), cancellable = true)
	private void transferables$addStress(CallbackInfoReturnable<Float> cir) {
		if (!(transferables$sender && !transferables$receiver))
			return;
		PortalAccess partner = transferables$partner();
		float stressSU = partner == null ? 0f : partner.create$localDemand();
		transferables$lastExportedStressSU = stressSU;
		float baseStress = transferables$toBase(stressSU, transferables$self().getTheoreticalSpeed());
		this.lastStressApplied = baseStress;
		cir.setReturnValue(baseStress);
	}
}
