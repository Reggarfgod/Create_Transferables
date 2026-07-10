package com.reggarf.mods.transferables.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.network.PortalShaftLink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class ShaftPortalMixin implements PortalAccess {


    @Shadow @Nullable public Long network;      // network ID (a Long!), null == no network
    @Shadow @Nullable public BlockPos source;
    @Shadow protected float capacity;           // network max stress (actual SU capacity)
    @Shadow protected float stress;             // network current stress (actual SU used)
    @Shadow protected float lastStressApplied;  // base stress impact, not final SU
    @Shadow protected float lastCapacityProvided; // base capacity, not final SU

    @Shadow public abstract float getSpeed();
    @Shadow public abstract float getTheoreticalSpeed();
    @Shadow public abstract void setSpeed(float speed);
    @Shadow public abstract void setNetwork(@Nullable Long networkIn);
    @Shadow public abstract void onSpeedChanged(float previousSpeed);
    @Shadow public abstract void attachKinetics();   // wraps RotationPropagator.handleAdded
    @Shadow public abstract void detachKinetics();   // wraps RotationPropagator.handleRemoved
    @Unique
    private static final float create$EPSILON = 1.0e-3f;
    @Unique
    private KineticBlockEntity create$self() {
       return (KineticBlockEntity) (Object) this;
    }
    @Unique @Nullable private ServerLevel create$otherLevel;
    @Unique @Nullable private BlockPos create$otherPos;
    @Unique private int create$cooldown = 0;
    @Unique private boolean create$receiver = false;
    @Unique private boolean create$sender = false;
    @Unique private float create$provided = 0f; // imported capacity as final SU
    @Unique private float create$lastGenerated = 0f;
    @Unique private float create$lastImportedCapacitySU = 0f;
    @Unique private float create$lastExportedStressSU = 0f;
    @Unique private float create$detachingGeneratedSpeed = 0f;

    @Unique private boolean create$isConnected = false;
    @Unique @Nullable private Direction create$portalDirection = null;

    @Inject(method = "tick", at = @At("HEAD"))
    private void create$repairGhostNetwork(CallbackInfo ci) {
       KineticBlockEntity self = create$self();
       if (!(self.getLevel() instanceof ServerLevel level))
          return;
       if (network != null || getTheoreticalSpeed() == 0f || source == null)
          return;
       if (!level.isLoaded(source))
          return;
       if (!(level.getBlockEntity(source) instanceof KineticBlockEntity sourceBE))
          return;
       if (sourceBE.network == null && sourceBE.getTheoreticalSpeed() != 0f)
          sourceBE.setNetwork(sourceBE.getBlockPos().asLong());
       if (sourceBE.network != null)
          setNetwork(sourceBE.network);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void create$portalTick(CallbackInfo ci) {
       KineticBlockEntity self = create$self();
       if (!(self.getLevel() instanceof ServerLevel serverLevel))
          return;

       BlockState state = self.getBlockState();
       if (PortalShaftLink.getPortalShaftAxis(state) == null)
          return;

       if (--create$cooldown <= 0) {
          create$cooldown = 20;
          create$resolveLink(serverLevel, state);
       }

       PortalAccess partner = create$partner();

       create$sender = create$computeSender();
       boolean nextReceiver = partner != null && partner.create$isPortalSender() && !create$sender;

       float gen = nextReceiver && partner != null ? partner.create$currentSpeed() : 0f;
       if (create$changed(gen, create$lastGenerated) || nextReceiver != create$receiver) {
          float previousGenerated = create$lastGenerated;
          create$applyGeneratedSpeed(gen, nextReceiver, previousGenerated);
          create$lastGenerated = gen;
       } else {
          create$receiver = nextReceiver;
       }

       create$syncStressTransfer(partner);
    }

    @Unique
    private void create$resolveLink(ServerLevel serverLevel, BlockState state) {
       create$otherLevel = null;
       create$otherPos = null;
       create$isConnected = false;
       create$portalDirection = null;

       Direction.Axis axis = PortalShaftLink.getPortalShaftAxis(state);
       if (axis == null)
          return;
       BlockPos pos = create$self().getBlockPos();

       Direction[] ends = {
             Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
             Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)
       };

       for (Direction dir : ends) {
          PortalShaftLink.PortalShaftEndpoint exit = PortalShaftLink.resolveShaft(serverLevel, pos, dir);
          if (exit != null) {
             create$otherLevel = exit.level();
             create$otherPos = exit.shaftPos();
             create$isConnected = true;
             create$portalDirection = dir;
             return;
          }
       }
    }

    @Unique @Nullable
    private PortalAccess create$partner() {
       if (create$otherLevel == null || create$otherPos == null)
          return null;
       if (!create$otherLevel.isLoaded(create$otherPos)) // never force-load chunks
          return null;
       return create$otherLevel.getBlockEntity(create$otherPos) instanceof PortalAccess a ? a : null;
    }

    @Unique
    private boolean create$computeSender() {
       if (network == null || getSpeed() == 0f)
          return false;
       return capacity - create$provided > create$EPSILON;
    }

    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
    private void create$genSpeed(CallbackInfoReturnable<Float> cir) {
       if (create$detachingGeneratedSpeed != 0f) {
          cir.setReturnValue(create$detachingGeneratedSpeed);
          return;
       }
       if (!create$receiver)
          return; // sender / idle side is a passive shaft, generates nothing
       PortalAccess partner = create$partner();
       cir.setReturnValue(partner == null ? 0f : partner.create$currentSpeed());
    }

    @Unique
    private void create$applyGeneratedSpeed(float gen, boolean nextReceiver, float previousGenerated) {
       KineticBlockEntity self = create$self();
       float prev = getSpeed();

       create$detachingGeneratedSpeed = create$receiver ? previousGenerated : 0f;
       detachKinetics();
       create$detachingGeneratedSpeed = 0f;

       create$receiver = nextReceiver;
       this.source = null; // a source roots itself
       setSpeed(gen);
       if (!nextReceiver) {
          create$provided = 0f;
          create$lastImportedCapacitySU = 0f;
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
    private void create$syncStressTransfer(@Nullable PortalAccess partner) {
       KineticBlockEntity self = create$self();
       if (network == null) {
          create$provided = 0f;
          create$lastImportedCapacitySU = 0f;
          create$lastExportedStressSU = 0f;
          return;
       }

       float importedCapacitySU = create$receiver && partner != null ? partner.create$exportableSU() : 0f;
       if (create$changed(importedCapacitySU, create$lastImportedCapacitySU)) {
          create$lastImportedCapacitySU = importedCapacitySU;
          create$provided = importedCapacitySU;
          if (create$receiver) {
             float baseCapacity = create$toBase(importedCapacitySU, self.getGeneratedSpeed());
             this.lastCapacityProvided = baseCapacity;
             self.getOrCreateNetwork().updateCapacityFor(self, baseCapacity);
          }
          self.sendData();
       }

       float exportedStressSU = create$sender && !create$receiver && partner != null ? partner.create$localDemand() : 0f;
       if (create$changed(exportedStressSU, create$lastExportedStressSU)) {
          create$lastExportedStressSU = exportedStressSU;
          float baseStress = create$toBase(exportedStressSU, self.getTheoreticalSpeed());
          this.lastStressApplied = baseStress;
          self.getOrCreateNetwork().updateStressFor(self, baseStress);
          self.sendData();
       }
    }

    @Unique
    private float create$toBase(float actualSU, float speed) {
       float absSpeed = Math.abs(speed);
       if (actualSU <= create$EPSILON || absSpeed <= create$EPSILON)
          return 0f;
       return actualSU / absSpeed;
    }

    @Unique
    private float create$toActual(float baseSU, float speed) {
       return Math.abs(baseSU * speed);
    }

    @Unique
    private boolean create$changed(float a, float b) {
       return Math.abs(a - b) > create$EPSILON;
    }

    // ========================= values the partner pulls across the portal =========================

    @Override
    public boolean create$isPortalSender() {
       return create$sender;
    }

    @Override
    public float create$currentSpeed() {
       return getSpeed();
    }

    @Override
    public float create$exportableSU() {
       if (network == null)
          return 0f;

       // lastStressApplied is a base value. Convert it back to final SU before
       // subtracting the portal's own echoed load from this side's local load.
       float portalStress = create$toActual(lastStressApplied, create$self().getTheoreticalSpeed());
       float localStress = Math.max(0f, stress - portalStress);
       return Math.max(0f, capacity - localStress);
    }

    @Override
    public float create$localDemand() {
       if (network == null)
          return 0f;

       float portalStress = create$toActual(lastStressApplied, create$self().getTheoreticalSpeed());
       return Math.max(0f, stress - portalStress);
    }

    @Inject(method = "calculateAddedStressCapacity", at = @At("HEAD"), cancellable = true)
    private void create$addCapacity(CallbackInfoReturnable<Float> cir) {
       if (!create$receiver)
          return;

       PortalAccess partner = create$partner();
       float capSU = partner == null ? 0f : partner.create$exportableSU();
       create$provided = capSU;
       create$lastImportedCapacitySU = capSU;

       float baseCapacity = create$toBase(capSU, create$self().getGeneratedSpeed());
       this.lastCapacityProvided = baseCapacity;
       cir.setReturnValue(baseCapacity);
    }

    @Inject(method = "calculateStressApplied", at = @At("HEAD"), cancellable = true)
    private void create$addStress(CallbackInfoReturnable<Float> cir) {
       if (!(create$sender && !create$receiver))
          return;

       PortalAccess partner = create$partner();
       float stressSU = partner == null ? 0f : partner.create$localDemand();
       create$lastExportedStressSU = stressSU;

       float baseStress = create$toBase(stressSU, create$self().getTheoreticalSpeed());
       this.lastStressApplied = baseStress;
       cir.setReturnValue(baseStress);
    }
}