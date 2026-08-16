package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import de.ultrabuild.trainsounds.Trainsounds;

// 🛡️ CORRECTION : Suppression du remap=false global qui cassait l'injection
@Mixin(ScheduleRuntime.class)
public abstract class ScheduleRuntimeMixin {

    @Shadow
    public Train train;
    @Shadow
    public Schedule schedule;
    @Shadow
    public int currentEntry;
    @Shadow
    public ScheduleRuntime.State state;

    @Unique
    private int trainsounds$waitTicksElapsed = 0;
    @Unique
    private boolean trainsounds$departureSoundPlayed = false;

    @Inject(method = "destinationReached", at = @At("TAIL"))
    private void trainsounds$resetTimerOnArrival(CallbackInfo ci) {
        trainsounds$waitTicksElapsed = 0;
        trainsounds$departureSoundPlayed = false;
    }

    @Inject(method = "tickConditions", at = @At("HEAD"))
    private void trainsounds$checkDepartureTime(Level level, CallbackInfo ci) {
        // Sécurité : on ne fait rien si le train n'est pas en station
        if (state != ScheduleRuntime.State.POST_TRANSIT)
            return;
        if (schedule == null || currentEntry >= schedule.entries.size())
            return;

        // On calcule nous-même le temps requis sans embêter les méthodes privées de
        // Create
        int totalWaitTicks = trainsounds$calculateWaitTime();

        if (totalWaitTicks > 0) {
            trainsounds$waitTicksElapsed++;

            if (!trainsounds$departureSoundPlayed && trainsounds$waitTicksElapsed >= (totalWaitTicks - 45)) {
                trainsounds$playDepartureSound(level);
                trainsounds$departureSoundPlayed = true;
            }
        }
    }

    // ==================================================
    // 🧠 NOUVELLE MÉTHODE : L'algorithme de temps
    // ==================================================
    @Unique
    private int trainsounds$calculateWaitTime() {
        if (currentEntry >= schedule.entries.size()) return -1;
        ScheduleEntry entry = schedule.entries.get(currentEntry);

        for (List<ScheduleWaitCondition> list : entry.conditions) {
            int total = 0;
            boolean onlyDelays = true;
            
            for (ScheduleWaitCondition condition : list) {
                if (condition instanceof ScheduledDelay wait) {
                    
                    // 🛡️ NOUVEAU CADENAS ANTI-CRN
                    // Si la condition de temps provient du code de l'addon CRN (mrjulsen), on l'exclut !
                    if (condition.getClass().getName().contains("mrjulsen")) {
                        onlyDelays = false;
                        break;
                    }
                    
                    total += wait.totalWaitTicks();
                } else {
                    onlyDelays = false;
                    break;
                }
            }
            
            if (onlyDelays && total > 0) {
                return total;
            }
        }
        return -1;
    }

    // ==================================================
    // 🔊 LECTURE DU SON
    // ==================================================
    @Unique
    private void trainsounds$playDepartureSound(Level level) {
        SoundEvent departureSound = trainsounds$getDepartureSoundForTrain();
        if (departureSound == null)
            return;

        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity != null) {
                float pitchJitter = 1.0f + (level.random.nextFloat() - 0.5f) * 0.02f;
                // Joue le son pour tous les joueurs depuis la position de l'entité
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), departureSound, SoundSource.NEUTRAL,
                        2.5f, pitchJitter);
            }
        }
    }

    @Unique
    private SoundEvent trainsounds$getDepartureSoundForTrain() {

        String trainName = train.name.getString().toLowerCase();

        if (trainName.contains("m7")) {
            return Trainsounds.M7_DEPARTURE_SOUND_EVENT.get();
        } else if (trainName.contains("m6")) {
            return Trainsounds.M6_DEPARTURE_SOUND_EVENT.get();
        } else if (trainName.contains("m1") || trainName.contains("m2") || trainName.contains("m3")
                || trainName.contains("m4")
                || trainName.contains("m5")) {
            return Trainsounds.MX_DEPARTURE_SOUND_EVENT.get();
        }

        return null;
    }
}