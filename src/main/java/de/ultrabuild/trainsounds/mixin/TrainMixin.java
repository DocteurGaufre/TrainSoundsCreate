package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin {

    // On importe les variables et méthodes dont on a besoin depuis Train.java
    @Shadow
    public double speed;
    @Shadow
    public double targetSpeed;
    @Shadow
    public boolean manualTick;

    @Shadow
    public abstract float maxSpeed();

    @Shadow
    public abstract float acceleration();

    @Shadow
    public abstract void leaveStation();

    @Inject(method = "approachTargetSpeed", at = @At("HEAD"), cancellable = true)
    private void trainsounds$customTrainPhysics(float accelerationMod, CallbackInfo ci) {
        double actualTarget = this.targetSpeed;

        if (Mth.equal(actualTarget, this.speed)) {
            ci.cancel();
            return;
        }

        if (this.manualTick) {
            this.leaveStation();
        }

        double baseAcceleration = this.acceleration();
        double absSpeed = Math.abs(this.speed);

        boolean isAccelerating = (this.speed >= 0 && actualTarget > this.speed) ||
                (this.speed <= 0 && actualTarget < this.speed);

        double physicsModifier = 1.0;

        if (isAccelerating) {
            // Modèle 2 : Le ratio est calculé par rapport à la vitesse max absolue (28
            // blocs/sec)
            double maxSpd = Math.max(this.maxSpeed(), 0.01);
            double speedRatio = Mth.clamp(absSpeed / maxSpd, 0.0, 1.0);

            // P = 1.5 (Courbe de puissance qui correspond aux chronos de 0 à 70 km/h)
            // M = 0.40 (Le plancher qui s'active au moment où le train atteint 70% de
            // sa vitesse)
            physicsModifier = Math.max(0.24, 1.0 - Math.pow(speedRatio, 0.75));
        } else {
            // Freinage adouci
            physicsModifier = 0.52;
        }

        double appliedAcceleration = baseAcceleration * accelerationMod * physicsModifier;

        if (this.speed < actualTarget) {
            this.speed = Math.min(this.speed + appliedAcceleration, actualTarget);
        } else if (this.speed > actualTarget) {
            this.speed = Math.max(this.speed - appliedAcceleration, actualTarget);
        }

        ci.cancel();
    }
}
