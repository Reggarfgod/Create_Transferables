package com.reggarf.mods.transferables.content.portal;

import javax.annotation.Nullable;

import com.google.common.base.Predicates;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Shared portal-link failure messages (mirrors Create's portal_track feedback). */
public final class PortalMessages {
	private PortalMessages() {}

	public static void failAndBreak(ServerLevel level, BlockPos pos, String langPrefix, @Nullable String reason,
	                                @Nullable BlockPos failPos) {
		notify(level, pos, langPrefix, reason, failPos);
		level.destroyBlock(pos, true);
	}

	public static void notify(ServerLevel level, BlockPos pos, String langPrefix, @Nullable String reason,
	                          @Nullable BlockPos failPos) {
		Player player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, Predicates.alwaysTrue());
		if (!(player instanceof ServerPlayer serverPlayer))
			return;

		serverPlayer.displayClientMessage(Component.literal(" ")
				.append(Component.translatable(langPrefix + ".failed").withStyle(ChatFormatting.GOLD)), false);

		if (reason == null)
			return;

		MutableComponent detail = failPos != null
				? Component.translatable(langPrefix + "." + reason, failPos.getX(), failPos.getY(), failPos.getZ())
				: Component.translatable(langPrefix + "." + reason);
		serverPlayer.displayClientMessage(Component.literal(" - ").withStyle(ChatFormatting.GRAY).append(detail), false);
	}
}
