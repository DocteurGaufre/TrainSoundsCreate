package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import de.ultrabuild.trainsounds.Trainsounds;

// 1. ASTUCE : On utilise "targets" avec le chemin en texte pour esquiver l'erreur d'importation !
@Mixin(targets = "de.mrjulsen.crn.data.schedule.condition.TrainSeparationCondition", remap = false)
public abstract class TrainSeparationConditionMixin {

    @Unique
    private static final Map<UUID, Integer> trainsounds$departureTimers = new HashMap<>();

    // 2. LA CIBLE PARFAITE : On intercepte juste avant l'écriture dans l'historique
    @Inject(method = "runDelayed", at = @At(value = "INVOKE", target = "Lde/mrjulsen/crn/data/train/DepartureHistory;updateDepartures(Ljava/lang/String;Lcom/simibubi/create/content/trains/entity/Train;)V", remap = false), cancellable = true, remap = false)
    private void trainsounds$interceptCrnDeparture(@Coerce Object context, CallbackInfoReturnable<Boolean> cir) {

        try {
            // 3. LA MAGIE : On utilise la "Réflexion" Java
            Method trainMethod = context.getClass().getMethod("train");
            Train train = (Train) trainMethod.invoke(context);

            Method levelMethod = context.getClass().getMethod("level");
            Level level = (Level) levelMethod.invoke(context);

            if (train == null || level == null)
                return;

            UUID trainId = train.id;
            int ticksWaited = trainsounds$departureTimers.getOrDefault(trainId, 0);

            if (ticksWaited == 0) {
                trainsounds$playDepartureSound(train, level);
            }

            // ⏱️ 45 ticks d'attente
            if (ticksWaited < 45) {
                trainsounds$departureTimers.put(trainId, ticksWaited + 1);

                // En forçant le retour à 'false', on coupe court à la méthode de CRN.
                // L'historique n'est pas mis à jour et le train reste à quai !
                cir.setReturnValue(false);
            } else {
                trainsounds$departureTimers.remove(trainId);
                // On ne fait rien : le code de CRN s'exécute normalement.
            }

        } catch (Exception e) {
            // Sécurité absolue
            System.out.println("[TrainSounds] Erreur de lecture de CRN : " + e.getMessage());
        }
    }

    // ==================================================
    // 🔊 LECTURE DU SON
    // ==================================================
    @Unique
    private void trainsounds$playDepartureSound(Train train, Level level) {
        SoundEvent departureSound = trainsounds$getDepartureSoundForTrain(train);
        if (departureSound == null)
            return;

        int totalCarriages = train.carriages.size();

        for (int i = 0; i < totalCarriages; i++) {
            // Uniquement la première et la dernière voiture
            if (i == 0 || i == totalCarriages - 1) {
                Carriage carriage = train.carriages.get(i);
                CarriageContraptionEntity entity = carriage.anyAvailableEntity();

                if (entity != null) {
                    float pitchJitter = 1.0f + (level.random.nextFloat() - 0.5f) * 0.02f;
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), departureSound,
                            SoundSource.NEUTRAL,
                            1.0f, pitchJitter);
                }
            }
        }
    }

    @Unique
    private SoundEvent trainsounds$getDepartureSoundForTrain(Train train) {

        String trainName = train.name.getString().toLowerCase();

        if (trainName.contains("m7")) {
            return Trainsounds.M7_DEPARTURE_SOUND_EVENT.get();
        } else if (trainName.contains("m6")) {
            return Trainsounds.M6_DEPARTURE_SOUND_EVENT.get();
        } else if (trainName.contains("m1") || trainName.contains("m2") || trainName.contains("m3")
                || trainName.contains("m4") || trainName.contains("mx")) {
            return Trainsounds.MX_DEPARTURE_SOUND_EVENT.get();
        }

        return null;
    }
}