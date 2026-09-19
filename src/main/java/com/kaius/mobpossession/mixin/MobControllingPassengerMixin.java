package com.kaius.mobpossession.mixin;

import com.kaius.mobpossession.possession.PossessionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Vanilla's Mob#getControllingPassenger() only ever returns a value for
 * mob-riding-mob jockeys (skeleton on spider, etc.) - it explicitly refuses
 * to recognize a Player as a controller for a generic mob.
 *
 * When a mob is being possessed, we short-circuit that and say "yes, this
 * player is controlling it." That one change is what makes vanilla's own
 * riding physics (gravity, collision, speed) kick in for us in
 * LivingEntity#aiStep(), instead of us having to reimplement all of that
 * by hand.
 */
@Mixin(Mob.class)
public abstract class MobControllingPassengerMixin {

	@Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
	private void mobpossession$possessedControllingPassenger(CallbackInfoReturnable<LivingEntity> cir) {
		Mob self = (Mob) (Object) this;
		UUID possessorId = PossessionManager.findPossessorOf(self.getUUID());
		if (possessorId == null) return;

		if (!(self.level() instanceof ServerLevel serverLevel)) return;
		if (serverLevel.getServer() == null) return;

		ServerPlayer possessor = serverLevel.getServer().getPlayerList().getPlayer(possessorId);
		if (possessor != null) {
			cir.setReturnValue(possessor);
		}
	}
}
