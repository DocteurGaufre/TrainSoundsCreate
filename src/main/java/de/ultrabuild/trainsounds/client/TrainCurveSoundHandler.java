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

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty())
                continue;

            // Si le train est à l'arrêt ou presque, on ignore tous ses wagons
            // (Optimisation)
            if (Math.abs(train.speed) < 0.05)
                continue;

            // ==========================================
            // ANALYSE INDÉPENDANTE DE CHAQUE WAGON
            // ==========================================
            for (Carriage carriage : train.carriages) {

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

                        // On place le son au centre exact du wagon
                        Vec3 trailingPos = trailingBogey.getAnchorPosition();
                        if (trailingPos != null) {
                            if (carriagePos != null) {
                                carriagePos = carriagePos.add(trailingPos).scale(0.5);
                            } else {
                                carriagePos = trailingPos;
                            }
                        }
                    }
                }

                // ==========================================
                // GESTION DU SON (Attaché au wagon)
                // ==========================================
                CurveSquealSoundInstance squeal = ACTIVE_SQUEALS.get(carriage);

                if (isCarriageInCurve && squeal == null && carriagePos != null) {
                    squeal = new CurveSquealSoundInstance(Trainsounds.CURVE_SOUND_EVENT.get(), carriagePos);
                    squeal.updateState(isCarriageInCurve, train.speed, carriagePos);
                    Minecraft.getInstance().getSoundManager().play(squeal);
                    ACTIVE_SQUEALS.put(carriage, squeal);
                }

                if (squeal != null) {
                    // Le son voyage en permanence avec son wagon !
                    squeal.updateState(isCarriageInCurve, train.speed, carriagePos);

                    if (squeal.isStopped() || squeal.canBeRemoved()) {
                        ACTIVE_SQUEALS.remove(carriage);
                    }
                }
            }
        }
    }
}
