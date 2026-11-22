package com.reggarf.mods.transferables.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reggarf.mods.transferables.api.PortalAccess;
import com.reggarf.mods.transferables.api.PortalBinding;
import com.reggarf.mods.transferables.api.PortalPumpAccess;
import com.reggarf.mods.transferables.content.portal.PortalDropEvents;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(value = WrenchItem.class, remap = false)
public class WrenchItemMixin {
	@Inject(method = "useOn", at = @At("HEAD"))
	private void transferables$useOnHead(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		Player player = context.getPlayer();
		if (player == null || !player.isShiftKeyDown())
			return;

		Level level = context.getLevel();
		if (!(level instanceof ServerLevel))
			return;

		PortalDropEvents.WRENCHING.set(true);

		BlockPos pos = context.getClickedPos();
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof KineticBlockEntity && be instanceof PortalAccess access
				&& access.transferables$isPortalConnected()) {
			PortalBinding partner = access.transferables$getBoundPartner();
			if (partner != null) {
				PortalDropEvents.suppressNextDrop(partner.dimension(), partner.pos());
			}
		}

		if (be instanceof SmartBlockEntity smart) {
			FluidTransportBehaviour behaviour = smart.getBehaviour(FluidTransportBehaviour.TYPE);
			if (behaviour instanceof PortalPumpAccess pumpAccess
					&& pumpAccess.transferables$isPumpPortalConnected()) {
				PortalBinding partner = pumpAccess.transferables$getPumpBoundPartner();
				if (partner != null) {
					PortalDropEvents.suppressNextDrop(partner.dimension(), partner.pos());
				}
			}
		}
	}

	@Inject(method = "useOn", at = @At("RETURN"))
	private void transferables$useOnReturn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		PortalDropEvents.WRENCHING.set(false);
	}
}
