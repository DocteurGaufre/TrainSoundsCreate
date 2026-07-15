package de.ultrabuild.trainsounds.client;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackEdge;
import de.ultrabuild.trainsounds.Trainsounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Trainsounds.MOD_ID, value = Dist.CLIENT)
public class TrainCurveSoundHandler {

    private static final Map<UUID, CurveSquealSoundInstance> ACTIVE_SQUEALS = new HashMap<>();

    private static boolean isHorizontalCurve(TrackEdge edge) {
        if (edge == null || !edge.isTurn())
            return false;

        Vec3 dir1 = edge.getDirection(true).multiply(1, 0, 1).normalize();
        Vec3 dir2 = edge.getDirection(false).multiply(1, 0, 1).normalize();

        double dist = dir1.distanceTo(dir2);
        double distOpposite = dir1.distanceTo(dir2.scale(-1));

        return dist > 0.02 && distOpposite > 0.02;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null)
            return;

        Vec3 playerPos = mc.player.position();

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty())
                continue;

            boolean isTrainInCurve = false;

            // On cherche UNIQUEMENT la position la plus proche du joueur
            Vec3 closestAnyPos = null;
            double minAnyDist = Double.MAX_VALUE;

            for (Carriage carriage : train.carriages) {

                // --- VÉRIFICATION DU BOGIE AVANT ---
                CarriageBogey leadingBogey = carriage.leadingBogey();
                if (leadingBogey != null) {
                    // Est-ce que cette roue crisse ?
                    if (isHorizontalCurve(leadingBogey.leading().edge)
                            || isHorizontalCurve(leadingBogey.trailing().edge)) {
                        isTrainInCurve = true;
                    }

                    // Où est cette roue par rapport au joueur ?
                    Vec3 pos = leadingBogey.getAnchorPosition();
                    if (pos != null) {
                        double dist = pos.distanceToSqr(playerPos);
                        if (dist < minAnyDist) {
                            minAnyDist = dist;
                            closestAnyPos = pos;
                        }
                    }
                }

                // --- VÉRIFICATION DU BOGIE ARRIÈRE ---
                if (carriage.isOnTwoBogeys()) {
                    CarriageBogey trailingBogey = carriage.trailingBogey();
                    if (trailingBogey != null) {
                        if (isHorizontalCurve(trailingBogey.leading().edge)
                                || isHorizontalCurve(trailingBogey.trailing().edge)) {
                            isTrainInCurve = true;
                        }

                        Vec3 pos = trailingBogey.getAnchorPosition();
                        if (pos != null) {
                            double dist = pos.distanceToSqr(playerPos);
                            if (dist < minAnyDist) {
                                minAnyDist = dist;
                                closestAnyPos = pos;
                            }
                        }
                    }
                }
            }

            // CORRECTION ULTIME : Le son est TOUJOURS accroché à la partie du train la plus
            // proche.
            // Il ne fera plus jamais de téléportation, ce qui supprime les pics de volume
            // et les coupures !
            Vec3 trainLocation = closestAnyPos;

            CurveSquealSoundInstance squeal = ACTIVE_SQUEALS.get(train.id);

            if (isTrainInCurve && Math.abs(train.speed) > 0.05 && squeal == null && trainLocation != null) {
                squeal = new CurveSquealSoundInstance(Trainsounds.CURVE_SOUND_EVENT.get(), trainLocation);
                squeal.updateState(isTrainInCurve, train.speed, trainLocation);
                Minecraft.getInstance().getSoundManager().play(squeal);
                ACTIVE_SQUEALS.put(train.id, squeal);
            }

            if (squeal != null) {
                squeal.updateState(isTrainInCurve, train.speed, trainLocation);

                if (squeal.isStopped() || squeal.canBeRemoved()) {
                    ACTIVE_SQUEALS.remove(train.id);
                }
            }
        }
    }
}
