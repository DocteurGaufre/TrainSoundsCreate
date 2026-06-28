package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSounds;
import de.ultrabuild.trainsounds.Trainsounds;
import de.ultrabuild.trainsounds.client.config.TrainSoundVolumeConfigManager;
import de.ultrabuild.trainsounds.logic.EngineToggleCarrier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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

    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/trains/entity/CarriageSounds;playIfMissing(Lnet/minecraft/client/Minecraft;Lcom/simibubi/create/content/trains/entity/CarriageSounds$LoopingSound;Lnet/minecraft/sounds/SoundEvent;)Lcom/simibubi/create/content/trains/entity/CarriageSounds$LoopingSound;",
                    ordinal = 0
            ),
            index = 2,
            remap = false
    )
    private SoundEvent trainsounds$muteMinecartLoop(SoundEvent original) {
        if (!trainsounds$shouldUseCustomEngineSound(entity)) {
            return original;
        }

        return SoundEvents.EMPTY;
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/AllSoundEvents$SoundEntry;playAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;FFZ)V"
            ),
            remap = false
    )
    private void trainsounds$muteVanillaSteam(
            AllSoundEvents.SoundEntry soundEntry,
            Level world,
            Vec3 soundLocation,
            float volume,
            float pitch,
            boolean fade
    ) {
        if (soundEntry == AllSoundEvents.STEAM && trainsounds$shouldUseCustomEngineSound(entity)) {
            return;
        }

        soundEntry.playAt(world, soundLocation, volume, pitch, fade);
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

        boolean isElectric = selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get() || selectedSound == Trainsounds.DEFAULT_SOUND_EVENT.get() || selectedSound == Trainsounds.DIESEL_SOUND_EVENT.get();

        Vec3 soundLocation = carriageEntity.position();
        double speedPerTick = trainsounds$getTrainSpeedPerTick(carriageEntity);
        float basePitch = trainsounds$dynamicPitchFromTrainSpeed(carriageEntity, 1.0f);
        float baseVolume = Mth.clamp((float) (speedPerTick * 18.0f), 0.20f, 2.5f) * userVolume;

        float maxSpeedPerTick = Math.max(carriageEntity.getCarriage().train.maxSpeed(), 0.001f);
        float normalizedSpeed = Mth.clamp((float) (speedPerTick / maxSpeedPerTick), 0.0f, 1.0f);

        // =====================================================================
        // 🎛️ RÉGLAGE DE L'ÉTOUFFEMENT DYNAMIQUE POUR LA M7
        float m7MuffleFactor = 0.05f; 
        float currentMuffle = 1.0f; // Par défaut, 100% du volume
        
        if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {
            if (normalizedSpeed <= 0.15f) {
                // De 0% à 15% : étouffement total
                currentMuffle = m7MuffleFactor;
            } else if (normalizedSpeed <= 0.30f) {
                // De 15% à 30% : on retire l'étouffement progressivement
                float unMuffleProgress = (normalizedSpeed - 0.15f) / 0.15f; 
                currentMuffle = Mth.lerp(unMuffleProgress, m7MuffleFactor, 1.0f);
            }
        }
        // =====================================================================

        long pulseTime = world.getGameTime();
        int phaseOffset = Math.floorMod(carriageEntity.getId(), 7);

        if (speedPerTick >= 0.001) {
            if ((pulseTime + phaseOffset) % 3 == 0) {
                
                float actualBaseVol = baseVolume * 1.25f * currentMuffle;

                world.playLocalSound(
                        soundLocation.x,
                        soundLocation.y,
                        soundLocation.z,
                        selectedSound,
                        SoundSource.NEUTRAL,
                        Mth.clamp(actualBaseVol, 0.25f, 3.5f),
                        Mth.clamp(basePitch * 1.05f, 0.5f, 2.5f),
                        false
                );

                // Sons additionnels M7
                if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {
                    
                    // Son 1 : Aigu (0% à 15%)
                    if (normalizedSpeed > 0.0f && normalizedSpeed <= 0.15f) {
                        float fadeIn = normalizedSpeed / 0.15f;
                        float start1Volume = Mth.clamp(fadeIn * 2.5f * userVolume, 0.1f, 3.0f);
                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.M7_START1_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                start1Volume, 1.0f, false
                        );
                    }

                    // Son 2 : Grave (10% à 35%)
                    if (normalizedSpeed > 0.10f && normalizedSpeed <= 0.35f) {
                        float fadeOut = 1.0f;
                        if (normalizedSpeed > 0.30f) {
                            // Fade Out progressif sur les derniers 5% (entre 30% et 35%)
                            fadeOut = Mth.clamp(1.0f - ((normalizedSpeed - 0.30f) / 0.05f), 0.0f, 1.0f);
                        }
                        // Le multiplicateur et le plafond sont augmentés à 6.0f pour un son plus fort !
                        float start2Volume = Mth.clamp(fadeOut * 6.0f * userVolume, 0.0f, 6.0f);
                        world.playLocalSound(
                                soundLocation.x, soundLocation.y, soundLocation.z,
                                Trainsounds.M7_START2_SOUND_EVENT.get(), SoundSource.NEUTRAL,
                                start2Volume, 1.0f, false
                        );
                    }
                }
            }

            if ((pulseTime + phaseOffset) % 9 == 0) {
                float actualBaseVol = baseVolume * 1.9f * currentMuffle;

                world.playLocalSound(
                        soundLocation.x,
                        soundLocation.y,
                        soundLocation.z,
                        selectedSound,
                        SoundSource.NEUTRAL,
                        Mth.clamp(actualBaseVol, 0.35f, 4.0f),
                        Mth.clamp(basePitch * 0.82f, 0.5f, 2.5f),
                        false
                );
            }
            return;
        }

        if ((pulseTime + phaseOffset) % 6 == 0) {
            float actualIdleVol = 0.9f * userVolume * currentMuffle;

            world.playLocalSound(
                    soundLocation.x,
                    soundLocation.y,
                    soundLocation.z,
                    selectedSound,
                    SoundSource.NEUTRAL,
                    actualIdleVol,
                    0.45f,
                    false
            );
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

        // Keep compatibility with setups where the carrier mixin is temporarily unavailable.
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
                        && e.getCarriage().train == carriage.train
        )) {
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
            case "traditional" -> Trainsounds.DEFAULT_SOUND_EVENT.get(); //MX
            case "modern" -> Trainsounds.DIESEL_SOUND_EVENT.get();       //M6
            case "electric" -> Trainsounds.ELECTRIC_SOUND_EVENT.get();   //M7
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

        float maxSpeedPerTick = Math.max(carriageEntity.getCarriage().train.maxSpeed(), 0.001f);
        
        // 1. On calcule le pourcentage de vitesse normale (de 0.0 à 1.0)
        float normalizedSpeed = Mth.clamp((float) (speedPerTick / maxSpeedPerTick), 0.0f, 1.0f);
        
        // 2. MODIFICATION : On booste ce pourcentage. 
        // En divisant par 0.70f, quand normalizedSpeed est à 0.7, effectiveSpeed est à 1.0 !
        // Le Mth.clamp empêche le son de continuer à monter si on dépasse les 70%.
        float effectiveSpeed = Mth.clamp(normalizedSpeed / 0.70f, 0.0f, 1.0f);
        
        // 3. On applique la courbe sur cette nouvelle vitesse "boostée"
        float curved = (float) Math.pow(effectiveSpeed, 0.65f);
        
        // 4. On interpole entre le pitch d'arrêt (0.70) et le pitch maximum (2.0)
        float pitchScale = Mth.lerp(curved, 0.70f, 2.00f);
        
        return Mth.clamp(basePitch * pitchScale, 0.5f, 2.0f);
    }

    @Unique
    private double trainsounds$getTrainSpeedPerTick(CarriageContraptionEntity carriageEntity) {
        double positionDelta = carriageEntity.position().subtract(new Vec3(carriageEntity.xo, carriageEntity.yo, carriageEntity.zo)).length();
        Carriage carriage = carriageEntity.getCarriage();
        if (carriage != null && carriage.train != null) {
            return Math.max(Math.abs(carriage.train.speed), positionDelta);
        }

        return positionDelta;
    }
}