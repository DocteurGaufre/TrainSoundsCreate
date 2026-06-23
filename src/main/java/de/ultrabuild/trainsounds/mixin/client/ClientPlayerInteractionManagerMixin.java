package de.ultrabuild.trainsounds.mixin.client;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.ultrabuild.trainsounds.Trainsounds;
import de.ultrabuild.trainsounds.client.TrainSoundsClientHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void trainsounds$onClientInteractEntity(Player player, Entity entity, net.minecraft.world.phys.EntityHitResult ray, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(entity instanceof CarriageContraptionEntity carriageEntity)) {
            return;
        }

        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Trainsounds.ENGINE_TOGGLE_ITEM.get())) {
            return;
        }

        TrainSoundsClientHandler.openCarriageManagementScreen(carriageEntity);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}