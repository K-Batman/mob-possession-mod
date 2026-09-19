package com.kaius.mobpossession.possession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

import java.util.UUID;

/**
 * The actual possession mechanic.
 *
 * How it works, step by step:
 *
 * 1. Right-click a mob (empty hand) -> we "enter" it: the mob's own wandering
 *    AI turns off, and the player actually starts *riding* the mob
 *    (Entity#startRiding, forced since mobs don't normally accept riders).
 *
 * 2. Two mixins (see the mixin package) make vanilla think the possessing
 *    player is "driving" the mob the same way a rider drives a horse:
 *    - MobControllingPassengerMixin tells Mob#getControllingPassenger() to
 *      say "yes, this player is in control" for a possessed mob.
 *    - LivingEntityRiddenInputMixin translates the player's WASD into the
 *      mob's movement input.
 *    Because this plugs into vanilla's *own* riding code, gravity,
 *    collision, and speed all just work correctly - we don't have to fake
 *    any physics ourselves. Riding also means the camera naturally follows
 *    the mob, since that's just how riding works.
 *
 * 3. Sneak (shift) -> we "exit": mob AI turns back on, the player stops
 *    riding and becomes visible again. Same thing happens automatically if
 *    the mob dies or despawns while possessed.
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

		// Holding something? Let it do its normal thing (feed, bucket, name tag,
		// lead, whatever) instead of possessing - possession is an empty-hand move.
		if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;

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
		PossessionManager.start(playerId, mob.getUUID());
		player.startRiding(mob, true, true);
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
		}
		// Movement and facing are handled by the mixins now (LivingEntityRiddenInputMixin),
		// same as vanilla riding - nothing left to do here each tick.
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
		}
		player.stopRiding();
		player.setInvisible(false);
		PossessionManager.stop(playerId);
		player.sendOverlayMessage(Component.literal("You let go of the mob.").withStyle(ChatFormatting.GRAY));
	}

	private static Mob findMobByUUID(ServerPlayer player, UUID mobId) {
		if (mobId == null) return null;
		if (!(player.level() instanceof ServerLevel serverLevel)) return null;
		Entity entity = serverLevel.getEntity(mobId);
		if (entity instanceof Mob mob) return mob;
		return null;
	}
}
