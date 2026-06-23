package de.ultrabuild.trainsounds.logic;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.ultrabuild.trainsounds.Trainsounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Trainsounds.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TrainEngineToggleHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("TrainSounds/Toggle");
    private static final String DEBUG_SEPARATOR = "_____________";
    private static final Map<UUID, ToggleAttempt> LAST_TOGGLE_ATTEMPT = new HashMap<>();

    private record ToggleAttempt(long tick, UUID targetId, String source) {
    }

    private TrainEngineToggleHandler() {
    }

    public static void register() {
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Level world = event.getLevel();
        InteractionHand hand = event.getHand();
        Entity entity = event.getTarget();

        logBoundary("START", "use_entity", player, world, "hand=" + hand + ", entity=" + entity.getUUID());
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Trainsounds.ENGINE_TOGGLE_ITEM.get())) {
            logBoundary("FINISH", "use_entity", player, world, "result=PASS, reason=wrong_item");
            return;
        }

        if (!(entity instanceof CarriageContraptionEntity carriageEntity)) {
            logBoundary("FINISH", "use_entity", player, world, "result=PASS, reason=not_carriage");
            return;
        }

        if (!(carriageEntity instanceof EngineToggleCarrier carrier)) {
            logBoundary("FINISH", "use_entity", player, world, "result=PASS, reason=carrier_mixin_missing");
            return;
        }

        if (world.isClientSide) {
            logBoundary("FINISH", "use_entity", player, world, "result=PASS, reason=client_side");
            return;
        }

        logBoundary(
                "FINISH",
                "use_entity",
                player,
                world,
                "result=CONSUME (GUI opened on client), entity=" + carriageEntity.getUUID() + ", carriage_index=" + carriageEntity.carriageIndex
        );
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level world = event.getLevel();
        InteractionHand hand = event.getHand();

        logBoundary("START", "use_item", player, world, "hand=" + hand);
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Trainsounds.ENGINE_TOGGLE_ITEM.get())) {
            logBoundary("FINISH", "use_item", player, world, "result=PASS, reason=wrong_item");
            return;
        }

        if (world.isClientSide) {
            logBoundary("FINISH", "use_item", player, world, "result=PASS, reason=client_side");
            return;
        }

        CarriageContraptionEntity target = findCarriageInFront(player, world, 8.0d);
        if (target == null) {
            logBoundary("FINISH", "use_item", player, world, "result=PASS, reason=no_carriage_in_front");
            return;
        }

        if (!(target instanceof EngineToggleCarrier carrier)) {
            player.displayClientMessage(Component.literal("Carriage engine state mixin missing."), true);
            logBoundary("FINISH", "use_item", player, world, "result=CONSUME, reason=carrier_mixin_missing");
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        if (isDuplicateToggle(player, world, target, "use_item")) {
            logBoundary("FINISH", "use_item", player, world, "result=CONSUME, reason=duplicate_tick_target");
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            de.ultrabuild.trainsounds.network.TrainSoundsNetworking.sendOpenCarriageGuiPacket(serverPlayer, target);
        }

        logBoundary(
                "FINISH",
                "use_item",
                player,
                world,
                "result=CONSUME (sent GUI open packet), entity=" + target.getUUID() + ", carriage_index=" + target.carriageIndex
        );
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    private static void toggleCarriageEngine(Player player, CarriageContraptionEntity carriageEntity, EngineToggleCarrier carrier) {
        boolean before = carrier.trainsounds$isEngineBuiltIn();
        LOGGER.info(
                "{} toggle_engine START {} player={} entity={} carriage_index={} state_before={}",
                DEBUG_SEPARATOR,
                DEBUG_SEPARATOR,
                player.getUUID(),
                carriageEntity.getUUID(),
                carriageEntity.carriageIndex,
                before
        );
        carrier.trainsounds$toggleEngineBuiltIn();
        boolean enabled = carrier.trainsounds$isEngineBuiltIn();
        int carriageDisplayIndex = carriageEntity.carriageIndex + 1;
        LOGGER.info(
                "{} toggle_engine FINISH {} player={} entity={} carriage_index={} state_after={}",
                DEBUG_SEPARATOR,
                DEBUG_SEPARATOR,
                player.getUUID(),
                carriageEntity.getUUID(),
                carriageEntity.carriageIndex,
                enabled
        );
        player.displayClientMessage(Component.translatable(
                enabled ? "message.trainsounds.engine_on" : "message.trainsounds.engine_off",
                carriageDisplayIndex
        ), true);
    }

    private static CarriageContraptionEntity findCarriageInFront(Player player, Level world, double maxDistance) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f).normalize();
        Vec3 rayEnd = eyePos.add(lookDir.scale(maxDistance));
        AABB searchBox = player.getBoundingBox().inflate(maxDistance);

        List<CarriageContraptionEntity> candidates = world.getEntitiesOfClass(
                CarriageContraptionEntity.class,
                searchBox,
                entity -> entity != null && entity.isAlive()
        );

        return candidates.stream()
                .filter(entity -> {
                    Vec3 toEntity = entity.position().subtract(eyePos);
                    double forwardDistance = toEntity.dot(lookDir);
                    return forwardDistance > 0 && forwardDistance <= maxDistance;
                })
                .filter(entity -> entity.getBoundingBox().inflate(0.25f).clip(eyePos, rayEnd).isPresent())
                .min(Comparator.comparingDouble(entity -> {
                    Vec3 hitPos = entity.getBoundingBox().inflate(0.25f).clip(eyePos, rayEnd).orElse(entity.position());
                    return eyePos.distanceToSqr(hitPos);
                }))
                .orElse(null);
    }

    private static boolean isDuplicateToggle(Player player, Level world, CarriageContraptionEntity target, String source) {
        long now = world.getGameTime();
        UUID playerId = player.getUUID();
        UUID targetId = target.getUUID();

        ToggleAttempt previous = LAST_TOGGLE_ATTEMPT.put(playerId, new ToggleAttempt(now, targetId, source));
        boolean duplicate = previous != null && previous.tick == now && previous.targetId != null && previous.targetId.equals(targetId);

        LOGGER.info(
                "{} duplicate_check {} player={} tick={} source={} target={} duplicate={} previous_tick={} previous_target={} previous_source={}",
                DEBUG_SEPARATOR,
                DEBUG_SEPARATOR,
                playerId,
                now,
                source,
                targetId,
                duplicate,
                previous != null ? previous.tick : null,
                previous != null ? previous.targetId : null,
                previous != null ? previous.source : null
        );

        return duplicate;
    }

    private static void logBoundary(String edge, String source, Player player, Level world, String details) {
        LOGGER.info(
                "{} {} {} player={} tick={} side={} details={} {}",
                DEBUG_SEPARATOR,
                source,
                edge,
                player.getUUID(),
                world.getGameTime(),
                world.isClientSide ? "client" : "server",
                details,
                DEBUG_SEPARATOR
        );
    }
}