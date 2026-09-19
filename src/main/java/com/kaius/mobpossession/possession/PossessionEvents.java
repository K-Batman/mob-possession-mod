package com.kaius.mobpossession.possession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.RangedAttackMob;

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
 * Extras:
 * - Flying mobs (anything with gravity turned off, like a Wither or Ghast)
 *   get real 3D flight control - see LivingEntityRiddenInputMixin.
 * - Ranged mobs (anything implementing RangedAttackMob, like a Wither) fire
 *   their normal attack at whatever you left-click while possessing them,
 *   instead of punching it.
 * - Water-only mobs (fish, squid, dolphins) drain your air bar while out of
 *   water, same feel as a player almost-drowning, so you know to get back in.
 */
public final class PossessionEvents {

	private PossessionEvents() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(PossessionEvents::onUseEntity);
		AttackEntityCallback.EVENT.register(PossessionEvents::onAttackEntity);
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

	/** Left-click while possessing a ranged mob (a Wither, say) fires its attack instead of punching. */
	private static InteractionResult onAttackEntity(net.minecraft.world.entity.player.Player playerEntity, net.minecraft.world.level.Level level, InteractionHand hand, Entity target, net.minecraft.world.phys.EntityHitResult hitResult) {
		if (!(playerEntity instanceof ServerPlayer player)) return InteractionResult.PASS;
		if (!(target instanceof LivingEntity livingTarget)) return InteractionResult.PASS;

		UUID mobId = PossessionManager.getPossessedMob(player.getUUID());
		Mob mob = findMobByUUID(player, mobId);
		if (mob == null) return InteractionResult.PASS;

		if (mob instanceof RangedAttackMob rangedAttackMob) {
			rangedAttackMob.performRangedAttack(livingTarget, 1.0F);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
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
		// Movement and facing are handled by the mixins now (LivingEntityRiddenInputMixin),
		// same as vanilla riding - nothing left to do here each tick.

		if (isAquatic(mob)) {
			tickAquaticBreath(player, mob);
		}
	}

	/** Fish, squid, dolphins - anything that can't actually breathe air. */
	private static boolean isAquatic(Mob mob) {
		return mob instanceof WaterAnimal || mob instanceof AgeableWaterCreature;
	}

	/**
	 * Borrows the player's own air-supply bar (the vanilla drowning bubbles)
	 * to show "your mob needs water" instead of adding a whole new HUD.
	 * Vanilla's own out-of-water damage on the mob still applies on top of this -
	 * this is purely the warning, not the danger.
	 */
	private static void tickAquaticBreath(ServerPlayer player, Mob mob) {
		if (mob.isInWater()) {
			player.setAirSupply(Math.min(player.getAirSupply() + 4, player.getMaxAirSupply()));
		} else {
			player.setAirSupply(Math.max(player.getAirSupply() - 1, 0));
		}
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
		player.setAirSupply(player.getMaxAirSupply()); // don't leave them drowning from the mob's air bar
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
