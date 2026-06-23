package de.ultrabuild.trainsounds.mixin;

import net.minecraft.core.HolderLookup;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.ultrabuild.trainsounds.logic.EngineToggleCarrier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarriageContraptionEntity.class)
public abstract class CarriageContraptionEntityMixin implements EngineToggleCarrier {

    @Unique
    private static final String TRAINSOUNDS_ENGINE_NBT_KEY = "TrainSoundsEngineBuiltIn";

    @Unique
    private static final EntityDataAccessor<Boolean> TRAINSOUNDS_ENGINE_BUILT_IN =
            SynchedEntityData.defineId(CarriageContraptionEntity.class, EntityDataSerializers.BOOLEAN);

    @Unique
    private boolean trainsounds$engineStateLoadedFromNbt = false;

    @Inject(method = "defineSynchedData", at = @At("TAIL"), remap = false)
    private void trainsounds$initEngineTracker(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(TRAINSOUNDS_ENGINE_BUILT_IN, false);
    }

    @Inject(method = "setCarriage", at = @At("TAIL"), remap = false)
    private void trainsounds$applyDefaultEngineState(Carriage carriage, CallbackInfo ci) {
        if (trainsounds$engineStateLoadedFromNbt) {
            return;
        }
        CarriageContraptionEntity self = (CarriageContraptionEntity) (Object) this;
        trainsounds$setEngineBuiltIn(self.carriageIndex == 0);
    }

    @Inject(method = "writeAdditional", at = @At("TAIL"), remap = false)
    private void trainsounds$writeEngineState(CompoundTag compound, HolderLookup.Provider provider, boolean spawnPacket, CallbackInfo ci) {
        compound.putBoolean(TRAINSOUNDS_ENGINE_NBT_KEY, trainsounds$isEngineBuiltIn());
    }

    @Inject(method = "readAdditional", at = @At("TAIL"), remap = false)
    private void trainsounds$readEngineState(CompoundTag compound, boolean spawnPacket, CallbackInfo ci) {
        CarriageContraptionEntity self = (CarriageContraptionEntity) (Object) this;

        if (compound.contains(TRAINSOUNDS_ENGINE_NBT_KEY)) {
            trainsounds$setEngineBuiltIn(compound.getBoolean(TRAINSOUNDS_ENGINE_NBT_KEY));
            trainsounds$engineStateLoadedFromNbt = true;
            return;
        }

        trainsounds$setEngineBuiltIn(self.carriageIndex == 0);
    }

    @Override
    public boolean trainsounds$isEngineBuiltIn() {
        CarriageContraptionEntity self = (CarriageContraptionEntity) (Object) this;
        return self.getEntityData().get(TRAINSOUNDS_ENGINE_BUILT_IN);
    }

    @Override
    public void trainsounds$setEngineBuiltIn(boolean enabled) {
        CarriageContraptionEntity self = (CarriageContraptionEntity) (Object) this;
        self.getEntityData().set(TRAINSOUNDS_ENGINE_BUILT_IN, enabled);
    }
}