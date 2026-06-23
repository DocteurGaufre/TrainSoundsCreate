package de.ultrabuild.trainsounds.mixin;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSounds;
import de.mrjulsen.paw.blockentity.PantographBlockEntity;
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

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
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

        String channel = trainsounds$resolveChannel(selectedSound);
        float userVolume = TrainSoundVolumeConfigManager.getVolumeMultiplier(channel);
        if (userVolume <= 0.0f) {
            return;
        }

        if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get() && !trainsounds$hasLivePantographOnTrain(carriageEntity)) {
            return;
        }

        Vec3 soundLocation = carriageEntity.position();
        double speedPerTick = trainsounds$getTrainSpeedPerTick(carriageEntity);
        float basePitch = trainsounds$dynamicPitchFromTrainSpeed(carriageEntity, 1.0f);
        float baseVolume = Mth.clamp((float) (speedPerTick * 18.0f), 0.20f, 2.5f) * userVolume;

        long pulseTime = world.getGameTime();
        int phaseOffset = Math.floorMod(carriageEntity.getId(), 7);

        if (speedPerTick >= 0.001) {
            if ((pulseTime + phaseOffset) % 3 == 0) {
                world.playLocalSound(
                        soundLocation.x,
                        soundLocation.y,
                        soundLocation.z,
                        selectedSound,
                        SoundSource.NEUTRAL,
                        Mth.clamp(baseVolume * 1.25f, 0.25f, 3.5f),
                        Mth.clamp(basePitch * 1.05f, 0.5f, 2.5f),
                        false
                );
            }

            if ((pulseTime + phaseOffset) % 9 == 0) {
                world.playLocalSound(
                        soundLocation.x,
                        soundLocation.y,
                        soundLocation.z,
                        selectedSound,
                        SoundSource.NEUTRAL,
                        Mth.clamp(baseVolume * 1.9f, 0.35f, 4.0f),
                        Mth.clamp(basePitch * 0.82f, 0.5f, 2.5f),
                        false
                );
            }
            return;
        }

        if ((pulseTime + phaseOffset) % 6 == 0) {
            world.playLocalSound(
                    soundLocation.x,
                    soundLocation.y,
                    soundLocation.z,
                    selectedSound,
                    SoundSource.NEUTRAL,
                    0.9f * userVolume,
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
        Contraption contraption = carriageEntity.getContraption();
        if (contraption == null || contraption.getBlocks() == null || contraption.getBlocks().isEmpty()) {
            return false;
        }

        for (BlockPos localPos : contraption.getBlocks().keySet()) {
            var blockEntity = contraption.getBlockEntityClientSide(localPos);
            if (blockEntity instanceof PantographBlockEntity pantographBlockEntity && pantographBlockEntity.isExpanded()) {
                return true;
            }
        }

        return false;
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
        return "electric".equals(icon) || "modern".equals(icon);
    }

    @Unique
    private SoundEvent trainsounds$selectEngineSound(CarriageContraptionEntity carriageEntity) {
        if (carriageEntity == null || carriageEntity.getCarriage() == null || carriageEntity.getCarriage().train == null
                || carriageEntity.getCarriage().train.icon == null) {
            return null;
        }

        String icon = carriageEntity.getCarriage().train.icon.getId().getPath();

        return switch (icon) {
            case "electric" -> Trainsounds.ELECTRIC_SOUND_EVENT.get();
            case "modern" -> Trainsounds.DIESEL_SOUND_EVENT.get();
            default -> null;
        };
    }

    @Unique
    private String trainsounds$resolveChannel(SoundEvent selectedSound) {
        if (selectedSound == Trainsounds.DIESEL_SOUND_EVENT.get()) {
            return "diesel";
        }

        if (selectedSound == Trainsounds.ELECTRIC_SOUND_EVENT.get()) {
            return "electric";
        }

        return "aux";
    }

    @Unique
    private float trainsounds$dynamicPitchFromTrainSpeed(CarriageContraptionEntity carriageEntity, float basePitch) {
        double speedPerTick = trainsounds$getTrainSpeedPerTick(carriageEntity);
        if (carriageEntity.getCarriage() == null || carriageEntity.getCarriage().train == null) {
            return Mth.clamp(basePitch, 0.5f, 2.5f);
        }

        float maxSpeedPerTick = Math.max(carriageEntity.getCarriage().train.maxSpeed(), 0.001f);
        float normalizedSpeed = Mth.clamp((float) (speedPerTick / maxSpeedPerTick), 0.0f, 1.0f);
        float curved = (float) Math.pow(normalizedSpeed, 0.65f);
        float pitchScale = Mth.lerp(curved, 0.95f, 1.45f);
        return Mth.clamp(basePitch * pitchScale, 0.5f, 2.5f);
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