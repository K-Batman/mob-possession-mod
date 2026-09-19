package com.kaius.mobpossession.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a possessed mob actually steer like a horse.
 *
 * Vanilla has a whole "ridden" pathway in LivingEntity (getRiddenInput /
 * getRiddenSpeed / tickRidden) that only horses and friends implement. We
 * implement it for any mob a player happens to be riding, which is exactly
 * the mobs our mod puts a player on.
 *
 * Three pieces, all copied from how AbstractHorse does it:
 *
 * 1. getRiddenInput - translate the rider's WASD into the mob's movement
 *    input (strafe halved, backing up quartered, same as vanilla horses).
 *
 * 2. getRiddenSpeed - the default returns the mob's internal `speed` field,
 *    which is only ever set by its AI goals. Our mobs have NoAi on, so that
 *    field sits at 0 and the mob would refuse to budge no matter the input.
 *    Horses dodge this by returning the MOVEMENT_SPEED attribute instead,
 *    so we do the same.
 *
 * 3. tickRidden - point the mob where the rider is looking, so "forward"
 *    means the direction you're facing.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRiddenInputMixin {

	/** Only drive mobs that a player is actually riding - never horses, boats, etc. */
	private boolean mobpossession$isPossessed(LivingEntity self, Player controller) {
		return self instanceof Mob && self.getFirstPassenger() == controller;
	}

	@Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true)
	private void mobpossession$getRiddenInput(Player controller, Vec3 selfInput, CallbackInfoReturnable<Vec3> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!mobpossession$isPossessed(self, controller)) return;

		float strafe = controller.xxa * 0.5F;
		float forward = controller.zza;
		if (forward <= 0.0F) {
			forward *= 0.25F; // walking backwards is slower, same as vanilla horses
		}

		cir.setReturnValue(new Vec3(strafe, 0.0, forward));
	}

	@Inject(method = "getRiddenSpeed", at = @At("HEAD"), cancellable = true)
	private void mobpossession$getRiddenSpeed(Player controller, CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!mobpossession$isPossessed(self, controller)) return;

		cir.setReturnValue((float) self.getAttributeValue(Attributes.MOVEMENT_SPEED));
	}

	@Inject(method = "tickRidden", at = @At("TAIL"))
	private void mobpossession$tickRidden(Player controller, Vec3 riddenInput, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!mobpossession$isPossessed(self, controller)) return;

		// Face where the rider is looking.
		self.setYRot(controller.getYRot());
		self.setXRot(controller.getXRot() * 0.5F);
		self.yHeadRot = self.getYRot();
		self.yBodyRot = self.getYRot();
		self.yRotO = self.getYRot();
	}
}
