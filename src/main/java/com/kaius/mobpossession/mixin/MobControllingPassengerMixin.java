package com.kaius.mobpossession.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's Mob#getControllingPassenger() only recognizes mob-riding-mob
 * jockeys (skeleton on spider). It flatly refuses to let a Player drive a
 * generic mob, and it bails out early if the mob has NoAi set.
 *
 * We say: if a player is riding this mob, that player is the driver.
 *
 * Why this specific check instead of looking up our own possession map:
 * this method has to give the same answer on the *client* as on the server.
 * Minecraft syncs passengers to clients automatically, so "who is riding me"
 * is known on both sides for free - whereas our possession bookkeeping only
 * exists on the server.
 *
 * That matters a lot, because Entity#isLocalClientAuthoritative() is built
 * on top of getControllingPassenger(). Once the client agrees it's driving,
 * the client simulates the mob's movement (gravity, collision, speed) and
 * reports the result to the server, exactly like riding a horse. If only the
 * server agreed, nobody would ever actually drive the thing.
 *
 * Note: vanilla rideables like horses override getControllingPassenger()
 * themselves, so this injection doesn't disturb them.
 */
@Mixin(Mob.class)
public abstract class MobControllingPassengerMixin {

	@Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
	private void mobpossession$playerRiderDrives(CallbackInfoReturnable<LivingEntity> cir) {
		Mob self = (Mob) (Object) this;
		Entity firstPassenger = self.getFirstPassenger();
		if (firstPassenger instanceof Player player) {
			cir.setReturnValue(player);
		}
	}
}
