package com.kaius.mobpossession.possession;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.UUID;

/**
 * The actual possession mechanic.
 *
 * How it works, step by step:
 *
 * 1. Right-click a mob (empty hand) -> we "enter" it: the mob's AI turns off
 *    (so it stops wandering on its own), the player goes invisible and gets
 *    parked at a fixed anchor spot, and we remember the pairing.
 *
 * 2. Every server tick, we look at how far the (invisible, parked) player
 *    *tried* to walk this tick using normal WASD - then we snap them back to
 *    the anchor and hand that same movement over to the mob instead, using
 *    Entity#move() so it still respects walls/collision. We also copy the
 *    player's look direction onto the mob's rotation, so where you look is
 *    where the mob faces.
 *
 * 3. Sneak (shift) -> we "exit": mob AI turns back on, player becomes
 *    visible again and is teleported to stand next to the mob. If the mob
 *    dies while possessed, the same thing happens automatically.
 *
 * Known limitation for v0.1: jumping isn't wired up yet (the mob won't jump
 * when you press space). That's the natural next thing to add.
 */
public final class PossessionEvents {

	private PossessionEvents() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(PossessionEvents::onUseEntity);
		ServerTickEvents.END_SERVER_TICK.register(PossessionEvents::onServerTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerDisconnect(handler.player));
	}

	private static InteractionResult onUseEntity(net.minecraft.world.entity.player.Player playerEntity, net.minecraft.world.level.Level level, InteractionHand hand, Entity target, net.minecraft.world.phys.EntityHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (!(playerEntity instanceof ServerPlayer player)) return InteractionResult.PASS;
		if (!(target instanceof Mob mob)) return InteractionResult.PASS; // players, item frames, etc. are off limits

		UUID playerId = player.getUUID();

		// Already possessing something? Sneak out of that one first.
		if (PossessionManager.isPossessing(playerId)) return InteractionResult.PASS;

		// Someone already inside this mob? Don't let a second player hijack it.
		if (PossessionManager.isMobPossessed(mob.getUUID())) {
			player.sendOverlayMessage(
					Component.literal("Someone else is already possessing that.").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}

		if (!mob.isAlive()) return InteractionResult.PASS;

		mob.setNoAi(true);

		Vec3 anchor = player.position();
		PossessionManager.start(playerId, mob.getUUID(), anchor);

		player.setInvisible(true);

		player.sendOverlayMessage(
				Component.literal("You are now controlling " + mob.getDisplayName().getString() + "! Sneak to get out.")
						.withStyle(ChatFormatting.GOLD));

		return InteractionResult.SUCCESS;
	}

	private static void onServerTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPossession(player);
		}
	}

	private static void tickPossession(ServerPlayer player) {
		UUID playerId = player.getUUID();
		if (!PossessionManager.isPossessing(playerId)) return;

		UUID mobId = PossessionManager.getPossessedMob(playerId);
		Mob mob = findMobByUUID(player, mobId);

		if (mob == null || !mob.isAlive()) {
			// Mob vanished out from under them somehow (despawned, died, etc.) - let them out.
			endPossession(player, mob);
			return;
		}

		if (player.isShiftKeyDown()) {
			endPossession(player, mob);
			return;
		}

		Vec3 anchor = PossessionManager.getAnchor(playerId);
		Vec3 lastPos = PossessionManager.getLastPosition(playerId);
		Vec3 currentPos = player.position();

		// How far did the player *try* to move this tick?
		Vec3 attemptedMove = currentPos.subtract(lastPos);

		// Snap the real body back to the anchor so it doesn't wander off while invisible.
		if (currentPos.distanceToSqr(anchor) > 0.0001) {
			player.teleportTo(anchor.x, anchor.y, anchor.z);
		}
		PossessionManager.setLastPosition(playerId, anchor);

		// Hand that movement to the mob instead. Keep the mob's own vertical
		// motion (gravity, falling) and only override horizontal movement.
		Vec3 mobMove = new Vec3(attemptedMove.x, 0, attemptedMove.z);
		if (mobMove.lengthSqr() > 0.00001) {
			mob.move(MoverType.SELF, mobMove);
		}

		// Look where the player looks.
		mob.setYRot(player.getYRot());
		mob.setXRot(player.getXRot());
		mob.yHeadRot = player.getYRot();
		mob.yBodyRot = player.getYRot();
		mob.yRotO = player.getYRot();
	}

	private static void onPlayerDisconnect(ServerPlayer player) {
		if (player == null) return;
		UUID playerId = player.getUUID();
		if (!PossessionManager.isPossessing(playerId)) return;

		UUID mobId = PossessionManager.getPossessedMob(playerId);
		Mob mob = findMobByUUID(player, mobId);
		if (mob != null) {
			mob.setNoAi(false);
		}
		PossessionManager.stop(playerId);
	}

	private static void endPossession(ServerPlayer player, Mob mob) {
		UUID playerId = player.getUUID();
		if (mob != null) {
			mob.setNoAi(false);
			player.teleportTo(mob.getX(), mob.getY(), mob.getZ());
			player.sendOverlayMessage(
					Component.literal("You let go of the mob.").withStyle(ChatFormatting.GRAY));
		}
		player.setInvisible(false);
		PossessionManager.stop(playerId);
	}

	private static Mob findMobByUUID(ServerPlayer player, UUID mobId) {
		if (mobId == null) return null;
		if (!(player.level() instanceof ServerLevel serverLevel)) return null;
		Entity entity = serverLevel.getEntity(mobId);
		if (entity instanceof Mob mob) return mob;
		return null;
	}
}
