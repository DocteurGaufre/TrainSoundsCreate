package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSounds;
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

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/animation/LerpedFloat;chase", ordinal = 3), index = 0, remap = false)
    private double trainsounds$modifySeatCrossfadeTarget(double originalTarget) {
        // Si c'est un train géré par notre mod
        if (trainsounds$shouldUseCustomEngineSound(entity)) {
            // 🎛️ RÉGLAGE : 0.3 = 30% d'étouffement.
            // Si le jeu essaie de nous asseoir (target > 0.0), on limite la valeur à 0.3
            return (originalTarget > 0.0) ? 0.2 : 0.0;
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

        // --- CORRECTION DU BUG DE SILENCE ICI ---
        // On contourne le ConfigManager (qui renvoyait 0) et on force le volume à 100%
        float userVolume = 1.0f;
        // ----------------------------------------

        Vec3 soundLocation = carriageEntity.position();
        double speedPerTick = trainsounds$getTrainSpeedPerTick(carriageEntity);
        float basePitch = trainsounds$dynamicPitchFromTrainSpeed(carriageEntity, 1.0f);
        float baseVolume = Mth.clamp((float) (speedPerTick * 18.0f), 0.20f, 2.5f) * userVolume;

        float maxSpeedPerTick = Math.max(carriageEntity.getCarriage().train.maxSpeed(), 0.001f);
        float normalizedSpeed = Mth.clamp((float) (speedPerTick / maxSpeedPerTick), 0.0f, 1.0f);

        // =====================================================================
        // 🎛️ RÉGLAGE DE L'ÉTOUFFEMENT DYNAMIQUE
        float currentMuffle = 1.0f; // Par défaut, 100% du volume

        // --- Profil M7 (Électrique) ---
        if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {
            if (normalizedSpeed <= 0.10f) {
                // De 0% à 15% : Le son de base est totalement silencieux
                currentMuffle = 0.0f;
            } else if (normalizedSpeed <= 0.60f) {
                // De 15% à 60% : Le son de base monte de 0% à 100%
                // La plage de montée dure maintenant 45% (0.60 - 0.15)
                float unMuffleProgress = (normalizedSpeed - 0.10f) / 0.50f;
                currentMuffle = Mth.lerp(unMuffleProgress, 0.0f, 1.0f);
            }
        }
        // --- Profil MX (Traditionnel/Défaut) ---
        else if (selectedSound == Trainsounds.DEFAULT_SOUND_EVENT.get()) {
            if (normalizedSpeed <= 0.15f) {
                // De 0% à 5% : Le son de base est totalement silencieux
                currentMuffle = 0.0f;
            } else if (normalizedSpeed <= 0.25f) {
                // De 5% à 25% : Le son de base monte de 0% à 100%
                float unMuffleProgress = (normalizedSpeed - 0.15f) / 0.10f;
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
                            Mth.clamp(actualBaseVol, 0.0f, 1.5f), // Plancher descendu à 0.0f
                            Mth.clamp((basePitch * 1.05f) + pitchJitter, 0.5f, 2.5f),
                            false);
                }

                // Sons additionnels M7
                if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {

                    // Son 1 : Aigu (0% à 15%)
                    if (normalizedSpeed > 0.0f && normalizedSpeed <= 0.15f) {
                        float fadeIn = normalizedSpeed / 0.15f;
                        float start1Volume = Mth.clamp(fadeIn * 1.5f * userVolume, 0.1f, 1.5f);
                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.M7_START1_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                start1Volume, 1.0f + pitchJitter, false);
                    }

                    // Son 2 : Grave (15% à 30%)
                    if (normalizedSpeed > 0.15f && normalizedSpeed <= 0.30f) {
                        float fadeOut = 1.0f;
                        float fadeIn = 1.0f;

                        // NOUVEAU : Fade In progressif sur les 2 premiers % (entre 10% et 12%)
                        if (normalizedSpeed <= 0.17f) {
                            fadeIn = Mth.clamp((normalizedSpeed - 0.15f) / 0.02f, 0.0f, 1.0f);
                        }

                        // Fade Out progressif sur les derniers 15% (entre 15% et 30%)
                        if (normalizedSpeed > 0.18f) {
                            fadeOut = Mth.clamp(1.0f - ((normalizedSpeed - 0.18f) / 0.05f), 0.0f, 1.0f);
                        }

                        // CORRECTION : On applique le fadeIn ET le fadeOut au calcul du volume final
                        float start2Volume = Mth.clamp(fadeIn * fadeOut * 1.5f * userVolume, 0.0f, 1.5f);

                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.M7_START2_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                start2Volume, 1.0f + pitchJitter, false);
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
                        float finalMxVol = Mth.clamp(mxStartVolume * 1.5f * userVolume, 0.0f, 1.5f);

                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.MX_START1_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                finalMxVol, 1.0f + pitchJitter, false);
                    }
                }

                // --------------------------------------------------
                // 💨 EFFET DE VENT AÉRODYNAMIQUE (Pour tous les trains)
                // Le vent commence à se faire entendre à 30% de la vitesse max
                if (normalizedSpeed > 0.05f) {

                    // Calcul de la montée en puissance (de 0.0 à 1.0 sur la plage 30% -> 100%)
                    float windFadeIn = (normalizedSpeed - 0.05f) / 0.95f;

                    // Le volume augmente exponentiellement avec la vitesse (pour simuler la
                    // pression de l'air)
                    float windVolumeCurve = (float) Math.pow(windFadeIn, 0.9);
                    float windVolume = Mth.clamp(windVolumeCurve * 1.5f * userVolume, 0.0f, 1.5f);

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

            if ((pulseTime + phaseOffset) % 9 == 0) {

                float pitchJitter = (world.random.nextFloat() - 0.5f) * 0.01f;

                float actualBaseVol = baseVolume * 1.9f * currentMuffle;

                // On ajoute notre sécurité ici
                if (actualBaseVol > 0.0f) {
                    world.playLocalSound(
                            soundLocation.x,
                            soundLocation.y,
                            soundLocation.z,
                            selectedSound,
                            SoundSource.NEUTRAL,
                            Mth.clamp(actualBaseVol, 0.0f, 2.0f), // <-- Plancher à 0.0f
                            Mth.clamp((basePitch * 0.82f) + pitchJitter, 0.5f, 2.5f),
                            false);
                }
            }

            return;
        }

        if ((pulseTime + phaseOffset) % 6 == 0) {
            float actualIdleVol = 0.5f * userVolume * currentMuffle;

            // NOUVEAU : On applique la même logique d'économie de ressources ici
            if (actualIdleVol > 0.0f) {
                world.playLocalSound(
                        soundLocation.x,
                        soundLocation.y,
                        soundLocation.z,
                        selectedSound,
                        SoundSource.NEUTRAL,
                        actualIdleVol,
                        0.45f,
                        false);
            }
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
    private boolean trainsounds$hasLivePantographContact(CarriageContraptionEntity carriageEntity) {
        return true;
    }

    @Unique
    private boolean trainsounds$hasLivePantographOnTrain(CarriageContraptionEntity carriageEntity) {
        Carriage carriage = carriageEntity.getCarriage();
        Level world = carriageEntity.level();
        if (carriage == null || carriage.train == null || world == null) {
            return trainsounds$hasLivePantographContact(carriageEntity);
        }

        for (CarriageContraptionEntity candidate : world.getEntitiesOfClass(
                CarriageContraptionEntity.class,
                carriageEntity.getBoundingBox().inflate(512.0d),
                e -> e != null
                        && e.isAlive()
                        && e.getCarriage() != null
                        && e.getCarriage().train == carriage.train)) {
            if (trainsounds$hasLivePantographContact(candidate)) {
                return true;
            }
        }

        return trainsounds$hasLivePantographContact(carriageEntity);
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
    private String trainsounds$resolveChannel(SoundEvent selectedSound) {
        if (selectedSound == Trainsounds.DEFAULT_SOUND_EVENT.get() ||
                selectedSound == Trainsounds.DIESEL_SOUND_EVENT.get() ||
                selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {
            return "electric"; // Vous pouvez grouper vos 3 métros sous le même curseur de volume
        }
        return "diesel";
    }

    @Unique
    private float trainsounds$dynamicPitchFromTrainSpeed(CarriageContraptionEntity carriageEntity, float basePitch) {
        double speedPerTick = trainsounds$getTrainSpeedPerTick(carriageEntity);
        if (carriageEntity.getCarriage() == null || carriageEntity.getCarriage().train == null) {
            return Mth.clamp(basePitch, 0.5f, 2.0f);
        }

        // Identifier le son utilisé pour ce train
        SoundEvent selectedSound = trainsounds$selectEngineSound(carriageEntity);

        float maxSpeedPerTick = Math.max(carriageEntity.getCarriage().train.maxSpeed(), 0.001f);

        // Pourcentage de vitesse (0.0 à 1.0)
        float normalizedSpeed = Mth.clamp((float) (speedPerTick / maxSpeedPerTick), 0.0f, 1.0f);

        // =================================================================
        // 🎛️ CALCUL DU PITCH SPÉCIFIQUE À LA RAME MX (DEFAULT_SOUND_EVENT)
        // =================================================================
        if (selectedSound == Trainsounds.DEFAULT_SOUND_EVENT.get()) {
            // Le son démarre à 1.0x (votre audio rabaissé)
            float pitchStart = 0.30f;

            // Le son monte jusqu'à 1.78x (pour annuler les -10 demi-tons)
            // Si vous trouvez que ça monte trop haut, vous pouvez baisser cette valeur (ex:
            // 1.50f)
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
}