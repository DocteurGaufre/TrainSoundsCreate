package de.ultrabuild.trainsounds.client;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackNode;
import de.ultrabuild.trainsounds.Trainsounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = Trainsounds.MOD_ID, value = Dist.CLIENT)
public class TrainSwitchSoundHandler {

    private static final Map<CarriageBogey, TrackNode> BOGEY_LAST_SWITCH = new WeakHashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null)
            return;

        // 🎯 On stocke la position des oreilles du joueur
        Vec3 playerPos = mc.player.position();

        // 🎟️ PASS VIP : On identifie le train actuel du joueur
        Train playerTrain = null;
        if (mc.player.getVehicle() instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce) {
            if (cce.getCarriage() != null) {
                playerTrain = cce.getCarriage().train;
            }
        }

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty() || train.graph == null)
                continue;
            if (Math.abs(train.speed) < 0.05)
                continue;

            for (Carriage carriage : train.carriages) {
                CarriageBogey leadingBogey = carriage.leadingBogey();

                // 🛡️ BOUCLIER SPATIAL (Ultra-rapide)
                // Avant de faire de la géométrie de graphe de rails, on vérifie si la voiture
                // est proche
                if (leadingBogey != null) {
                    Vec3 bogeyPos = leadingBogey.getAnchorPosition();
                    // 4096 = 64 blocs au carré
                    if (train != playerTrain && bogeyPos != null && bogeyPos.distanceToSqr(playerPos) > 4096.0) {
                        continue; // La voiture est trop loin, le processeur passe à la suivante !
                    }
                }

                // Si on arrive ici, c'est que le train est à moins de 64 blocs. On autorise les
                // calculs lourds !
                checkBogeyProximity(leadingBogey, train);

                if (carriage.isOnTwoBogeys()) {
                    checkBogeyProximity(carriage.trailingBogey(), train);
                }
            }
        }
    }

    private static void checkBogeyProximity(CarriageBogey bogey, Train train) {
        if (bogey == null || bogey.leading() == null)
            return;

        TrackNode n1 = bogey.leading().node1;
        TrackNode n2 = bogey.leading().node2;
        Vec3 bogeyPos = bogey.getAnchorPosition();

        if (n1 == null || n2 == null || bogeyPos == null)
            return;

        verifyNode(n1, bogey, train, bogeyPos);
        verifyNode(n2, bogey, train, bogeyPos);
    }

    private static void verifyNode(TrackNode node, CarriageBogey bogey, Train train, Vec3 bogeyPos) {
        if (train.graph.getConnectionsFrom(node).size() <= 2)
            return;

        Vec3 nodePos = node.getLocation().getLocation();
        double distanceSquared = nodePos.distanceToSqr(bogeyPos);

        double threshold = Math.max(1.5, Math.abs(train.speed) * 1.5);

        if (distanceSquared < threshold * threshold) {
            if (BOGEY_LAST_SWITCH.get(bogey) != node) {
                playClackSound(bogey, train);
                BOGEY_LAST_SWITCH.put(bogey, node);
            }
        } else {
            if (BOGEY_LAST_SWITCH.get(bogey) == node && distanceSquared > 25.0) {
                BOGEY_LAST_SWITCH.remove(bogey);
            }
        }
    }

    private static void playClackSound(CarriageBogey bogey, Train train) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        Vec3 pos = bogey.getAnchorPosition();
        if (pos == null)
            return;

        float speedAbs = (float) Math.abs(train.speed);
        float volume = Mth.clamp(speedAbs * 2.0f, 0.2f, 1.0f);
        float randomJitter = (mc.level.random.nextFloat() - 0.5f) * 0.2f;
        float pitch = Mth.clamp(1.0f + (speedAbs * 0.3f) + randomJitter, 0.8f, 1.4f);

        mc.level.playLocalSound(
                pos.x, pos.y, pos.z,
                Trainsounds.SWITCH_SOUND_EVENT.get(),
                SoundSource.NEUTRAL,
                volume,
                pitch,
                false);
    }
}
