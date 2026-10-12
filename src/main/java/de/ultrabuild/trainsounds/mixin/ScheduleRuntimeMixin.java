package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;

import net.minecraft.nbt.CompoundTag;
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
    @Shadow
    public List<Integer> conditionProgress;
    @Shadow
    public List<CompoundTag> conditionContext;

    // "true" par défaut. Si le train est posé ou reçoit un planning, il ne sonnera
    // pas.
    @Unique
    private boolean trainsounds$soundPlayed = true;
    @Unique
    private int trainsounds$doorTimer = 0;

    // Vérifie si le train est arrivé naturellement en roulant.
    @Unique
    private boolean trainsounds$wasAtStation = false;

    // 1. ARRIVÉE EN GARE : On "arme" le système de départ
    @Inject(method = "destinationReached", at = @At("TAIL"))
    private void trainsounds$onArrival(CallbackInfo ci) {
        trainsounds$soundPlayed = false;
        trainsounds$doorTimer = 0;
        trainsounds$wasAtStation = true; // Le train s'est arrêté à un quai

        // On vérifie tout de suite si l'arrêt est trop court pour un son
        trainsounds$evaluateShortStop();
    }

    // 2. L'ANTICIPATION VANILLA (Pour jouer le son AVANT le départ naturel)
    @Inject(method = "tickConditions", at = @At("HEAD"))
    private void trainsounds$anticipateVanilla(Level level, CallbackInfo ci) {
        if (level.isClientSide())
            return;
        if (trainsounds$soundPlayed)
            return;

        // Si CRN gère ce quai, on le laisse faire !
        if (trainsounds$hasCrnCondition())
            return;

        if (schedule == null || currentEntry >= schedule.entries.size())
            return;
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        if (entry.conditions == null || conditionProgress == null || conditionContext == null)
            return;

        for (int i = 0; i < entry.conditions.size(); i++) {
            if (i >= conditionProgress.size() || i >= conditionContext.size())
                continue;
            List<ScheduleWaitCondition> list = entry.conditions.get(i);
            int progress = conditionProgress.get(i);
            if (progress >= list.size())
                continue;

            ScheduleWaitCondition condition = list.get(progress);
            if (condition instanceof ScheduledDelay delay && !condition.getClass().getName().contains("mrjulsen")) {
                int elapsed = conditionContext.get(i).getInt("Time");
                int totalWait = delay.totalWaitTicks();

                // On déclenche le son exactement 45 ticks avant le départ naturel !
                if (totalWait >= 60 && (totalWait - elapsed) == 45) {
                    trainsounds$playDepartureSound(level);
                    trainsounds$soundPlayed = true;
                    return;
                }
            }
        }
    }

    // 3. LA SUSPENSION SÉCURISÉE (Pour CRN et les autres conditions)
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/schedule/ScheduleRuntime;startCurrentInstruction(Lnet/minecraft/world/level/Level;)Lcom/simibubi/create/content/trains/graph/DiscoveredPath;"), cancellable = true)
    private void trainsounds$holdDeparture(Level level, CallbackInfo ci) {
        if (level.isClientSide())
            return;

        // Si le train n'est jamais arrivé en gare (ex: on vient de lui donner le
        // planning)
        if (!trainsounds$wasAtStation) {
            return; // On le laisse partir immédiatement sans rien faire !
        }

        // Si le son n'a PAS été joué par anticipation (ex: fin des 15s de CRN)
        if (!trainsounds$soundPlayed) {

            // Premier tick du sas : on joue le son et on démarre le chrono de 45 ticks
            if (trainsounds$doorTimer == 0) {
                trainsounds$playDepartureSound(level);
                trainsounds$doorTimer = 45;
            }

            trainsounds$doorTimer--;

            if (trainsounds$doorTimer <= 0) {
                // Les portes sont fermées, le train est libre de partir !
                trainsounds$soundPlayed = true;
                trainsounds$wasAtStation = false; // Prêt pour la prochaine gare
            } else {
                ci.cancel(); // Bloque le calcul de la route le temps des 45 ticks.
            }
        } else {
            // Si le son a déjà été géré (Vanilla > 3s ou arrêt < 3s). On réinitialise
            // simplement la variable.
            trainsounds$wasAtStation = false;
        }
    }

    // ==================================================
    // 🧠 LOGIQUE D'ANALYSE
    // ==================================================
    @Unique
    private void trainsounds$evaluateShortStop() {
        if (trainsounds$hasCrnCondition())
            return; // CRN pourrait rallonger l'attente

        int totalWait = trainsounds$calculateWaitTime();
        // Si l'attente Vanilla totale est connue et inférieure à 3 secondes (60 ticks),
        // on désarme le buzzer !
        if (totalWait >= 0 && totalWait < 60) {
            trainsounds$soundPlayed = true;
        }
    }

    @Unique
    private boolean trainsounds$hasCrnCondition() {
        if (schedule == null || currentEntry >= schedule.entries.size())
            return false;
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        if (entry.conditions == null)
            return false;
        for (List<ScheduleWaitCondition> list : entry.conditions) {
            for (ScheduleWaitCondition condition : list) {
                if (condition.getClass().getName().contains("mrjulsen"))
                    return true;
            }
        }
        return false;
    }

    @Unique
    private int trainsounds$calculateWaitTime() {
        if (schedule == null || currentEntry >= schedule.entries.size())
            return -1;
        ScheduleEntry entry = schedule.entries.get(currentEntry);

        for (List<ScheduleWaitCondition> list : entry.conditions) {
            int total = 0;
            boolean onlyDelays = true;

            for (ScheduleWaitCondition condition : list) {
                if (condition instanceof ScheduledDelay wait) {
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

        int totalCarriages = train.carriages.size();
        for (int i = 0; i < totalCarriages; i++) {
            if (i == 0 || i == totalCarriages - 1) {
                Carriage carriage = train.carriages.get(i);
                CarriageContraptionEntity entity = carriage.anyAvailableEntity();
                if (entity != null) {
                    float pitchJitter = 1.0f + (level.random.nextFloat() - 0.5f) * 0.02f;
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), departureSound,
                            SoundSource.NEUTRAL, 1.0f, pitchJitter);
                }
            }
        }
    }

    @Unique
    private SoundEvent trainsounds$getDepartureSoundForTrain() {
        String trainName = train.name.getString().toLowerCase();
        if (trainName.contains("m7"))
            return Trainsounds.M7_DEPARTURE_SOUND_EVENT.get();
        if (trainName.contains("m6"))
            return Trainsounds.M6_DEPARTURE_SOUND_EVENT.get();
        if (trainName.contains("m1") || trainName.contains("m2") || trainName.contains("m3") || trainName.contains("m4")
                || trainName.contains("mx")) {
            return Trainsounds.MX_DEPARTURE_SOUND_EVENT.get();
        }
        return null;
    }
}