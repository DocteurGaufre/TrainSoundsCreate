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

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = Trainsounds.MOD_ID, value = Dist.CLIENT)
public class TrainCurveSoundHandler {

    // MÉMOIRE PAR WAGON : Utilisation d'une WeakHashMap.
    // Si un wagon est détruit ou déchargé, Java nettoie la mémoire automatiquement
    // !
    private static final Map<Carriage, CurveSquealSoundInstance> ACTIVE_SQUEALS = new WeakHashMap<>();

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

        // On enregistre la position de vos oreilles
        Vec3 playerPos = mc.player.position();

        // 🎟️ PASS VIP : On identifie le train actuel du joueur
        Train playerTrain = null;
        if (mc.player.getVehicle() instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce) {
            if (cce.getCarriage() != null) {
                playerTrain = cce.getCarriage().train;
            }
        }

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty())
                continue;

            boolean isTrainStopped = Math.abs(train.speed) < 0.05;

            // ==========================================
            // ANALYSE INDÉPENDANTE DE CHAQUE WAGON
            // ==========================================
            for (Carriage carriage : train.carriages) {
                CurveSquealSoundInstance squeal = ACTIVE_SQUEALS.get(carriage);

                boolean isCarriageInCurve = false;
                Vec3 carriagePos = null;

                // --- VÉRIFICATION DU BOGIE AVANT ---
                CarriageBogey leadingBogey = carriage.leadingBogey();
                if (leadingBogey != null) {
                    if (isHorizontalCurve(leadingBogey.leading().edge)
                            || isHorizontalCurve(leadingBogey.trailing().edge)) {
                        isCarriageInCurve = true;
                    }
                    carriagePos = leadingBogey.getAnchorPosition();
                }

                // --- VÉRIFICATION DU BOGIE ARRIÈRE ---
                if (carriage.isOnTwoBogeys()) {
                    CarriageBogey trailingBogey = carriage.trailingBogey();
                    if (trailingBogey != null) {
                        if (isHorizontalCurve(trailingBogey.leading().edge)
                                || isHorizontalCurve(trailingBogey.trailing().edge)) {
                            isCarriageInCurve = true;
                        }
                        Vec3 trailingPos = trailingBogey.getAnchorPosition();
                        if (trailingPos != null) {
                            carriagePos = (carriagePos != null) ? carriagePos.add(trailingPos).scale(0.5) : trailingPos;
                        }
                    }
                }

                // 🛡️ LE FILTRE SPATIAL (Le bouclier anti-surcharge)
                boolean isTooFar = true;
                if (train != playerTrain && carriagePos != null) {
                    // distanceToSqr est beaucoup plus rapide à calculer qu'une vraie distance (pas
                    // de racine carrée)
                    // 64 * 64 = 4096 (Ce qui équivaut à un rayon de 64 blocs autour du joueur)
                    isTooFar = carriagePos.distanceToSqr(playerPos) > 4096.0;
                }

                // Si le train est arrêté OU qu'il est à plus de 64 blocs de vous
                if (isTrainStopped || isTooFar) {
                    if (squeal != null) {
                        squeal.updateState(false, 0.0, null);
                        if (squeal.isStopped() || squeal.canBeRemoved()) {
                            ACTIVE_SQUEALS.remove(carriage);
                        }
                    }
                    continue; // On bloque l'exécution ici, le canal audio est sauvé !
                }

                // ==========================================
                // GESTION DU SON (Attaché au wagon)
                // ==========================================
                if (isCarriageInCurve && squeal == null && carriagePos != null) {
                    squeal = new CurveSquealSoundInstance(Trainsounds.CURVE_SOUND_EVENT.get(), carriagePos);
                    squeal.updateState(true, train.speed, carriagePos);
                    mc.getSoundManager().play(squeal);
                    ACTIVE_SQUEALS.put(carriage, squeal);
                }

                if (squeal != null) {
                    squeal.updateState(isCarriageInCurve, train.speed, carriagePos);
                    if (squeal.isStopped() || squeal.canBeRemoved()) {
                        ACTIVE_SQUEALS.remove(carriage);
                    }
                }
            }
        }
    }
}
