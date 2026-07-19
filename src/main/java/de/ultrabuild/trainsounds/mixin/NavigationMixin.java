package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.content.trains.entity.Navigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Navigation.class, remap = false)
public abstract class NavigationMixin {

    // On cible la création de la variable "double acceleration =
    // train.acceleration();" (ordinal = 0)
    @ModifyVariable(method = "tick", at = @At(value = "STORE", ordinal = 0), remap = false)
    private double trainsounds$adaptAIAcceleration(double originalAcceleration) {
        // Puisque nous avons imposé un 'physicsModifier = 0.7' pour le freinage dans
        // TrainMixin,
        // nous devons diviser l'accélération perçue par l'IA par 0.7.
        // Cela va forcer la variable 'brakingDistance' à doubler mathématiquement !
        return originalAcceleration * 0.7;
    }
}