package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSounds;
import com.simibubi.create.content.trains.entity.Train;

import de.ultrabuild.trainsounds.Trainsounds;
import de.ultrabuild.trainsounds.logic.EngineToggleCarrier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarriageSounds.class)
public abstract class CarriageSoundsMixin {

    @Shadow
    CarriageContraptionEntity entity;

    @Unique
    private double trainsounds$antiLagSpeed = 0.0;

    @Unique
    private long trainsounds$lastTickTime = 0;

    @Shadow
    public abstract void finalizeSharedVolume(float volume); // Permet d'exécuter l'ordre de silence

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/AllSoundEvents$SoundEntry;playAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;FFZ)V"), remap = false)
    private void trainsounds$muteVanillaSteam(
            AllSoundEvents.SoundEntry soundEntry,
            Level world,
            Vec3 soundLocation,
            float volume,
            float pitch,
            boolean fade) {
        if (soundEntry == AllSoundEvents.STEAM && trainsounds$shouldUseCustomEngineSound(entity)) {
            return;
        }

        soundEntry.playAt(world, soundLocation, volume, pitch, fade);
    }

    // ==================================================
    // 🔊 RENDRE LE SON DE ROULEMENT INDÉPENDANT PAR VOITURE
    // ==================================================

    @Shadow
    public abstract void submitSharedSoundVolume(Vec3 location, float volume);

    // 1. Intercepter l'envoi du son à la motrice pour l'appliquer à la voiture
    // ACTUELLE
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/CarriageSounds;submitSharedSoundVolume(Lnet/minecraft/world/phys/Vec3;F)V"), remap = false)
    private void trainsounds$applySoundLocally(CarriageSounds leadCarriageSounds, Vec3 location, float volume) {
        // On ignore 'leadCarriageSounds' (la motrice) sélectionnée par Create.
        // On force la voiture actuelle ('this') à mettre à jour son propre haut-parleur
        // local !
        this.submitSharedSoundVolume(location, volume);
    }

    // 2. Empêcher Create de rendre les wagons muets UNIQUEMENT pour la tête et la
    // queue
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/CarriageSounds;finalizeSharedVolume(F)V"), remap = false)
    private void trainsounds$preventMute(CarriageSounds instance, float volume) {
        // On récupère la position (l'index) de la voiture actuelle (0 = motrice)
        int index = this.entity.carriageIndex;

        // On récupère le nombre total de voitures qui composent ce train
        int totalCarriages = this.entity.getCarriage().train.carriages.size();

        // Est-ce la motrice (première) ou la dernière voiture du convoi ?
        if (index == 0 || index == totalCarriages - 1) {
            // C'est une extrémité : on annule purement et simplement l'ordre de silence
            // (mute)
            return;
        }

        // Si on arrive ici, c'est que c'est une voiture du milieu.
        // On exécute la vraie méthode de Create pour la rendre muette et économiser les
        // sons.
        this.finalizeSharedVolume(volume);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/animation/LerpedFloat;chase", ordinal = 3), index = 0, remap = false)
    private double trainsounds$modifySeatCrossfadeTarget(double originalTarget) {
        // Si c'est un train géré par notre mod
        if (trainsounds$shouldUseCustomEngineSound(entity)) {
            // 🎛️ RÉGLAGE : 0.1 = 10% d'étouffement.
            // Si le jeu essaie de nous asseoir (target > 0.0), on limite la valeur à 0.1
            return (originalTarget > 0.0) ? 0.1 : 0.0;
        }

        // Sinon, comportement normal de Create
        return originalTarget;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void trainsounds$playEnginePerDce(Carriage.DimensionalCarriageEntity dce, CallbackInfo ci) {
        CarriageContraptionEntity carriageEntity = trainsounds$resolveTargetCarriage(dce);
        if (carriageEntity == null || !carriageEntity.isAlive()) {
            return;
        }

        Level world = carriageEntity.level();
        if (world == null) {
            return;
        }

        if (!trainsounds$isEngineEnabledOnCarriage(carriageEntity)) {
            return;
        }

        if (!trainsounds$shouldUseCustomEngineSound(carriageEntity)) {
            return;
        }

        SoundEvent selectedSound = trainsounds$selectEngineSound(carriageEntity);
        if (selectedSound == null) {
            return;
        }

        float userVolume = net.minecraft.client.Minecraft.getInstance().options
                .getSoundSourceVolume(SoundSource.MASTER);

        // 🎧 NOUVEAU : Application de l'isolation acoustique si le son vient de
        // l'extérieur
        userVolume *= trainsounds$getAcousticIsolationMultiplier(carriageEntity);

        Vec3 soundLocation = carriageEntity.position();

        double rawSpeedPerTick = trainsounds$getTrainSpeedPerTick(carriageEntity);

        // ==================================================
        // 🛡️ FILTRE ANTI-LAG ULTIME (Anti-Catchup Temporel)
        // ==================================================
        long currentTime = System.currentTimeMillis();

        // Un tick normal prend 50ms. Si ce tick s'exécute moins de 25ms après le
        // précédent,
        // c'est que le jeu décharge un "Lag Spike" et rattrape son retard en rafale !
        boolean isCatchingUp = (currentTime - this.trainsounds$lastTickTime) < 25;
        this.trainsounds$lastTickTime = currentTime;

        if (!isCatchingUp) {
            if (rawSpeedPerTick > this.trainsounds$antiLagSpeed) {
                // Tolérance de 0.005 par tick (accélération maximale physique)
                this.trainsounds$antiLagSpeed = Math.min(rawSpeedPerTick, this.trainsounds$antiLagSpeed + 0.005);
            } else {
                // Tolérance de 0.015 pour la décélération / freinage
                this.trainsounds$antiLagSpeed = Math.max(rawSpeedPerTick, this.trainsounds$antiLagSpeed - 0.015);
            }
        }
        // Si isCatchingUp est VRAI, le filtre est ignoré. La variable antiLagSpeed
        // reste
        // figée à sa dernière valeur saine. Le son ne bougera pas d'un cheveu pendant
        // le freeze !

        // On passe la variable sécurisée pour la suite
        double speedPerTick = this.trainsounds$antiLagSpeed;
        // ==================================================

        // Tous ces calculs vont maintenant utiliser la vitesse lissée et protégée !
        float basePitch = trainsounds$dynamicPitchFromTrainSpeed(carriageEntity, speedPerTick, 1.0f);
        float baseVolume = Mth.clamp((float) (speedPerTick * 18.0f), 0.20f, 2.5f) * userVolume;

        float maxSpeedPerTick = Math.max(carriageEntity.getCarriage().train.maxSpeed(), 0.001f);
        float normalizedSpeed = Mth.clamp((float) (speedPerTick / maxSpeedPerTick), 0.0f, 1.0f);

        // =====================================================================
        // 🎛️ RÉGLAGE DE L'ÉTOUFFEMENT DYNAMIQUE
        float currentMuffle = 1.0f; // Par défaut, 100% du volume

        // --- Profil M7 (Électrique) ---
        if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {
            if (normalizedSpeed <= 0.175f) {
                // De 0% à 17.5% : Le son de base est totalement silencieux
                currentMuffle = 0.0f;
            } else if (normalizedSpeed <= 0.60f) {
                // De 17.5% à 60% : Le son de base monte de 0% à 100%
                // La plage de montée dure maintenant 42.5% (0.60 - 0.175 = 0.425)
                float unMuffleProgress = (normalizedSpeed - 0.175f) / 0.425f;
                currentMuffle = Mth.lerp(unMuffleProgress, 0.0f, 1.0f);
            }
        }
        // --- Profil MX (Traditionnel) ---
        else if (selectedSound == Trainsounds.DEFAULT_SOUND_EVENT.get()) {
            if (normalizedSpeed <= 0.10f) {
                // De 0% à 10% : Le son de base est totalement silencieux
                currentMuffle = 0.0f;
            } else if (normalizedSpeed <= 0.60f) {
                // De 10% à 60% : Le son de base monte de 0% à 100%
                float unMuffleProgress = (normalizedSpeed - 0.10f) / 0.50f;
                currentMuffle = Mth.lerp(unMuffleProgress, 0.0f, 1.0f);
            }
        }
        // --- Profil M6 (Diesel) ---
        else if (selectedSound == Trainsounds.DIESEL_SOUND_EVENT.get()) {
            if (normalizedSpeed <= 0.05f) {
                // De 0% à 5% : Le son de base est totalement silencieux
                currentMuffle = 0.0f;
            } else if (normalizedSpeed <= 0.60f) {
                // De 5% à 60% : Le son de base monte de 0% à 100%
                float unMuffleProgress = (normalizedSpeed - 0.05f) / 0.55f;
                currentMuffle = Mth.lerp(unMuffleProgress, 0.0f, 1.0f);
            }
        }
        // =====================================================================

        long pulseTime = world.getGameTime();
        int phaseOffset = Math.floorMod(carriageEntity.getId(), 7);

        if (speedPerTick >= 0.001) {
            if ((pulseTime + phaseOffset) % 3 == 0) {

                float pitchJitter = (world.random.nextFloat() - 0.5f) * 0.01f;

                float actualBaseVol = baseVolume * 1.25f * currentMuffle;

                if (actualBaseVol > 0.0f) {
                    world.playLocalSound(
                            soundLocation.x,
                            soundLocation.y,
                            soundLocation.z,
                            selectedSound,
                            SoundSource.NEUTRAL,
                            Mth.clamp(actualBaseVol, 0.0f, 1.0f), // Plancher descendu à 0.0f
                            Mth.clamp((basePitch * 1.05f) + pitchJitter, 0.5f, 2.5f),
                            false);
                }

                // Sons additionnels M7
                if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {

                    // Son 1 : Aigu (0% à 17.5%)
                    if (normalizedSpeed > 0.0f && normalizedSpeed <= 0.175f) {
                        // On ajuste la division pour correspondre au nouveau plafond de 17.5%
                        float fadeIn = normalizedSpeed / 0.175f;

                        float start1Volume = Mth.clamp(fadeIn * 1.0f * userVolume, 0.1f, 1.0f);
                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.M7_START1_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                start1Volume, 1.0f + pitchJitter, false);
                    }

                    // Son 2 : Grave (17.5% à 32.5%)
                    if (normalizedSpeed > 0.175f && normalizedSpeed <= 0.325f) {
                        float fadeOut = 1.0f;

                        // Fade Out progressif sur une plage de 10% (0.10f)
                        // Le fade out commence donc à 22.5% (0.325 - 0.10 = 0.225)
                        if (normalizedSpeed > 0.225f) {
                            fadeOut = 1.0f - ((normalizedSpeed - 0.225f) / 0.10f);
                        }

                        float start2Volume = Mth.clamp(fadeOut * 1.0f * userVolume, 0.0f, 1.0f);

                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.M7_START2_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                start2Volume, 1.0f + pitchJitter, false);
                    }
                }

                // --------------------------------------------------
                // Sons additionnels M6
                if (selectedSound == Trainsounds.DIESEL_SOUND_EVENT.get()) {

                    // Son M6 1 : De 0% à 25%
                    if (normalizedSpeed > 0.0f && normalizedSpeed <= 0.25f) {
                        float m6StartVolume = 1.0f;

                        // Fade In progressif de 0% à 5%
                        if (normalizedSpeed <= 0.05f) {
                            m6StartVolume = normalizedSpeed / 0.05f;
                        }

                        // Fade Out progressif de 10% à 25%
                        if (normalizedSpeed > 0.10f) {
                            m6StartVolume = 1.0f - ((normalizedSpeed - 0.10f) / 0.15f);
                        }

                        // Application du volume final
                        float finalM6Vol = Mth.clamp(m6StartVolume * 1.0f * userVolume, 0.0f, 1.0f);

                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.M6_START1_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                finalM6Vol, 1.0f + pitchJitter, false);
                    }
                }

                // --------------------------------------------------
                // Sons additionnels MX
                if (selectedSound == Trainsounds.DEFAULT_SOUND_EVENT.get()) {

                    // Son MX 1 : De 0% à 25%
                    if (normalizedSpeed > 0.0f && normalizedSpeed <= 0.25f) {
                        float mxStartVolume = 1.0f;

                        // Fade In progressif de 0% à 5%
                        if (normalizedSpeed <= 0.05f) {
                            mxStartVolume = normalizedSpeed / 0.05f;
                        }

                        // Fade Out progressif de 10% à 25%
                        if (normalizedSpeed > 0.10f) {
                            mxStartVolume = 1.0f - ((normalizedSpeed - 0.10f) / 0.15f);
                        }

                        // Application du volume final
                        float finalMxVol = Mth.clamp(mxStartVolume * 1.0f * userVolume, 0.0f, 1.0f);

                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.MX_START1_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                finalMxVol, 1.0f + pitchJitter, false);
                    }
                }

                // --------------------------------------------------
                // 💨 EFFET DE VENT AÉRODYNAMIQUE (Pour tous les trains)
                if (normalizedSpeed > 0.05f) {

                    float windFadeIn = (normalizedSpeed - 0.05f) / 0.95f;

                    // Le volume augmente exponentiellement avec la vitesse (pour simuler la
                    // pression de l'air)
                    float windVolumeCurve = (float) Math.pow(windFadeIn, 0.9);
                    float windVolume = Mth.clamp(windVolumeCurve * 1.0f * userVolume, 0.0f, 1.0f);

                    // Le pitch monte très légèrement pour simuler l'air qui siffle plus vite
                    // On y ajoute notre fameux pitchJitter pour éviter tout effet de
                    // wobble/robotique !
                    float windPitch = Mth.lerp(windFadeIn, 0.8f, 1.2f) + pitchJitter;

                    world.playLocalSound(
                            soundLocation.x, soundLocation.y, soundLocation.z,
                            Trainsounds.WIND_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                            windVolume,
                            windPitch,
                            false);
                }
            }

            return;
        }
    }

    @Unique
    private CarriageContraptionEntity trainsounds$resolveTargetCarriage(Carriage.DimensionalCarriageEntity dce) {
        if (dce != null && dce.entity != null) {
            CarriageContraptionEntity dceEntity = dce.entity.get();
            if (dceEntity != null) {
                return dceEntity;
            }
        }

        return entity;
    }

    @Unique
    private boolean trainsounds$isEngineEnabledOnCarriage(CarriageContraptionEntity carriageEntity) {
        if (carriageEntity instanceof EngineToggleCarrier carrier) {
            return carrier.trainsounds$isEngineBuiltIn();
        }

        // Keep compatibility with setups where the carrier mixin is temporarily
        // unavailable.
        return true;
    }

    @Unique
    private boolean trainsounds$shouldUseCustomEngineSound(CarriageContraptionEntity carriageEntity) {
        if (carriageEntity == null || carriageEntity.getCarriage() == null || carriageEntity.getCarriage().train == null
                || carriageEntity.getCarriage().train.icon == null) {
            return false;
        }

        String icon = carriageEntity.getCarriage().train.icon.getId().getPath();
        // On écoute les 3 identifiants de base de Create
        return "traditional".equals(icon) || "modern".equals(icon) || "electric".equals(icon);
    }

    @Unique
    private SoundEvent trainsounds$selectEngineSound(CarriageContraptionEntity carriageEntity) {
        if (carriageEntity == null || carriageEntity.getCarriage() == null || carriageEntity.getCarriage().train == null
                || carriageEntity.getCarriage().train.icon == null) {
            System.out.println("[TRAINSOUNDS DEBUG] Le train n'a pas d'icône !");
            return Trainsounds.DEFAULT_SOUND_EVENT.get();
        }

        String icon = carriageEntity.getCarriage().train.icon.getId().getPath();

        return switch (icon) {
            case "traditional" -> Trainsounds.DEFAULT_SOUND_EVENT.get(); // MX
            case "modern" -> Trainsounds.DIESEL_SOUND_EVENT.get(); // M6
            case "electric" -> Trainsounds.ELECTRIC_SOUND_EVENT.get(); // M7
            default -> {
                System.out.println("[TRAINSOUNDS DEBUG] L'icône n'est pas dans la liste !");
                yield Trainsounds.DEFAULT_SOUND_EVENT.get();
            }
        };
    }

    @Unique
    private float trainsounds$dynamicPitchFromTrainSpeed(CarriageContraptionEntity carriageEntity, double filteredSpeed,
            float basePitch) {
        // double speedPerTick = trainsounds$getTrainSpeedPerTick(carriageEntity);

        if (carriageEntity.getCarriage() == null || carriageEntity.getCarriage().train == null) {
            return Mth.clamp(basePitch, 0.5f, 2.0f);
        }

        // Identifier le son utilisé pour ce train
        SoundEvent selectedSound = trainsounds$selectEngineSound(carriageEntity);

        float maxSpeedPerTick = Math.max(carriageEntity.getCarriage().train.maxSpeed(), 0.001f);

        // ✅ MODIFIÉ : On utilise "filteredSpeed" (la vitesse lissée par notre anti-lag)
        float normalizedSpeed = Mth.clamp((float) (filteredSpeed / maxSpeedPerTick), 0.0f, 1.0f);

        // =================================================================
        // 🎛️ CALCUL DU PITCH SPÉCIFIQUE À LA RAME MX (DEFAULT_SOUND_EVENT)
        // =================================================================
        if (selectedSound == Trainsounds.DEFAULT_SOUND_EVENT.get()) {
            // Le son démarre à 1.0x (votre audio rabaissé)
            float pitchStart = 0.30f;

            // Le son monte jusqu'à 1.78x (pour annuler les -10 demi-tons)
            float pitchEnd = 1.40f;

            // On utilise une courbe exponentielle légère pour que la montée soit naturelle
            float curvedSpeed = (float) Math.pow(normalizedSpeed, 0.70f);

            float finalMxPitch = Mth.lerp(curvedSpeed, pitchStart, pitchEnd);

            // On s'assure de ne pas dépasser le maximum de Minecraft (2.0f)
            return Mth.clamp(basePitch * finalMxPitch, 0.3f, 2.0f);
        }

        // =================================================================
        // 🎛️ CALCUL DU PITCH POUR LES AUTRES TRAINS (M7, M6, etc.)
        // =================================================================
        float effectiveSpeed = Mth.clamp(normalizedSpeed / 0.70f, 0.0f, 1.0f);
        float curved = (float) Math.pow(effectiveSpeed, 0.65f);
        float pitchScale = Mth.lerp(curved, 0.10f, 2.00f);

        return Mth.clamp(basePitch * pitchScale, 0.1f, 2.0f);
    }

    @Unique
    private double trainsounds$getTrainSpeedPerTick(CarriageContraptionEntity carriageEntity) {
        double positionDelta = carriageEntity.position()
                .subtract(new Vec3(carriageEntity.xo, carriageEntity.yo, carriageEntity.zo)).length();
        Carriage carriage = carriageEntity.getCarriage();
        if (carriage != null && carriage.train != null) {
            return Math.max(Math.abs(carriage.train.speed), positionDelta);
        }

        return positionDelta;
    }

    // ==================================================
    // 🎧 ISOLATION ACOUSTIQUE (ANC)
    // ==================================================
    @Unique
    private float trainsounds$getAcousticIsolationMultiplier(CarriageContraptionEntity soundSourceEntity) {
        // 1. On récupère le joueur local (celui qui écoute)
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null)
            return 1.0f;

        // 2. On remonte l'arbre des entités (Joueur -> Siège -> Wagon)
        net.minecraft.world.entity.Entity rootVehicle = player.getRootVehicle();

        // 3. Est-ce que le véhicule final est bien un wagon de Create ?
        if (rootVehicle instanceof CarriageContraptionEntity playerCarriage) {

            Train playerTrain = playerCarriage.getCarriage().train;
            Train sourceTrain = soundSourceEntity.getCarriage().train;

            // 4. Si le son provient d'un train et que ce n'est PAS le nôtre, on l'étouffe !
            if (playerTrain != null && sourceTrain != null && playerTrain != sourceTrain) {
                // L'atténuation de 70%
                return 0.3f;
            }
        }

        // Si le joueur est à pied ou dans le même train, le volume reste à 100%
        return 1.0f;
    }
}