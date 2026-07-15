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

    // Garde en mémoire le dernier aiguillage frappé par chaque bogie
    private static final Map<CarriageBogey, TrackNode> BOGEY_LAST_SWITCH = new WeakHashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null)
            return;

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty() || train.graph == null)
                continue;
            if (Math.abs(train.speed) < 0.05)
                continue;

            for (Carriage carriage : train.carriages) {
                checkBogeyProximity(carriage.leadingBogey(), train);
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

        // On teste la proximité avec les deux extrémités du rail actuel
        verifyNode(n1, bogey, train, bogeyPos);
        verifyNode(n2, bogey, train, bogeyPos);
    }

    private static void verifyNode(TrackNode node, CarriageBogey bogey, Train train, Vec3 bogeyPos) {
        // Si le noeud n'a que 2 chemins, c'est un rail normal, on ignore
        if (train.graph.getConnectionsFrom(node).size() <= 2)
            return;

        Vec3 nodePos = node.getLocation().getLocation();
        double distanceSquared = nodePos.distanceToSqr(bogeyPos);

        // Rayon de détection (s'agrandit légèrement si le train roule très vite pour ne
        // pas le rater)
        double threshold = Math.max(1.5, Math.abs(train.speed) * 1.5);

        if (distanceSquared < threshold * threshold) {
            // Si on est proche de l'aiguillage et qu'on ne l'a pas encore "tapé"
            if (BOGEY_LAST_SWITCH.get(bogey) != node) {
                playClackSound(bogey, train);
                BOGEY_LAST_SWITCH.put(bogey, node); // On enregistre pour ne pas mitrailler le son
            }
        } else {
            // Si on s'est éloigné d'au moins 5 blocs (25 au carré) de ce noeud, on l'oublie
            // Ça permet de pouvoir re-déclencher le son si le train fait marche arrière !
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
