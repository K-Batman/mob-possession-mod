package com.kaius.mobpossession.mixin;

import com.kaius.mobpossession.possession.PossessionManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's LivingEntity#getRiddenInput() just hands back the mob's own
 * (AI-driven) input by default - only things like horses override it to
 * translate the rider's WASD into movement. We do the same trick here for
 * whichever mob is currently possessed, using the same math horses use:
 * strafe from the rider's xxa, forward from the rider's zza (slowed down
 * a bit for backing up, just like vanilla does).
 *
 * This runs *inside* vanilla's own travel/gravity/collision code, so we get
 * correct physics for free instead of faking it ourselves.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRiddenInputMixin {

	@Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true)
	private void mobpossession$getRiddenInput(Player controller, Vec3 selfInput, CallbackInfoReturnable<Vec3> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Mob mob)) return;
		if (!PossessionManager.isMobPossessed(mob.getUUID())) return;

		float strafe = controller.xxa * 0.5F;
		float forward = controller.zza;
		if (forward <= 0.0F) {
			forward *= 0.25F; // walking backwards is slower, same as vanilla horses
		}

		cir.setReturnValue(new Vec3(strafe, selfInput.y, forward));
	}
}
