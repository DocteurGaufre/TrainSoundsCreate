package de.ultrabuild.trainsounds.client;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.ultrabuild.trainsounds.network.packet.TrainAnnouncePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientAnnounceHandler {

    public static void handle(TrainAnnouncePacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null)
                return;

            // 1. Vérification du véhicule
            if (!(mc.player.getVehicle() instanceof CarriageContraptionEntity carriage))
                return;

            // 2. Vérification de l'ID du train
            if (carriage.getCarriage() == null || carriage.getCarriage().train == null)
                return;
            if (!carriage.getCarriage().train.id.equals(data.trainId()))
                return;

            // 3. LECTURE DU SON (Constructeur complet)
            SimpleSoundInstance sound = new SimpleSoundInstance(
                    data.soundId(), // <--- CORRECTION : On passe directement le ResourceLocation reçu du réseau !
                    SoundSource.VOICE, // Assignation au canal "Voix/Paroles" des options
                    1.0F, // Volume
                    1.0F, // Pitch (vitesse)
                    RandomSource.create(), // Générateur aléatoire
                    false, // Répétition (Non)
                    0, // Délai (0)
                    SoundInstance.Attenuation.NONE, // Pas d'atténuation 3D (son Interface)
                    0.0, 0.0, 0.0, // Coordonnées (ignorées car relatif)
                    true // Relatif au joueur ("dans la tête")
            );

            mc.getSoundManager().play(sound);
        });
    }
}