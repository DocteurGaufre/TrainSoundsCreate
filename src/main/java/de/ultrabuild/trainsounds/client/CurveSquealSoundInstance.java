package de.ultrabuild.trainsounds.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class CurveSquealSoundInstance extends AbstractTickableSoundInstance {

    private float targetVolume = 0.0f;
    private float currentVolume = 0.0f;

    public CurveSquealSoundInstance(SoundEvent squealEvent, Vec3 initialLocation) {
        super(squealEvent, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.looping = true; // C'est une boucle infinie
        this.delay = 0;
        this.volume = 0.01f; // On commence en silence
        this.pitch = 1.0f;
        this.x = initialLocation.x;
        this.y = initialLocation.y;
        this.z = initialLocation.z;
    }

    public void updateState(boolean inCurve, double speed, Vec3 newLocation) {

        // On met à jour la position 3D UNIQUEMENT si on nous en donne une valide.
        // Si le train n'est plus dans la courbe, le son restera figé là où il a été
        // entendu pour la dernière fois et fera son fondu en douceur !
        if (newLocation != null) {
            this.x = newLocation.x;
            this.y = newLocation.y;
            this.z = newLocation.z;
        }

        if (inCurve && Math.abs(speed) > 0.05) {
            this.targetVolume = Mth.clamp((float) Math.abs(speed * 2.5), 0.0f, 1.0f);
            this.pitch = Mth.clamp(1.0f + (float) Math.abs(speed), 1.0f, 1.5f);
        } else {
            this.targetVolume = 0.0f;
        }
    }

    @Override
    public void tick() {
        // Fondu (Fade) super fluide
        if (this.currentVolume < this.targetVolume) {
            this.currentVolume = Math.min(this.currentVolume + 0.1f, this.targetVolume); // Montée rapide (0.5s)
        } else if (this.currentVolume > this.targetVolume) {
            this.currentVolume = Math.max(this.currentVolume - 0.03f, this.targetVolume); // Descente lente (1.5s)
        }

        this.volume = this.currentVolume;

        if (this.canBeRemoved()) {
            this.stop();
        }
    }

    // NOUVELLE MÉTHODE POUR LE HANDLER : Vérifie si le son est mort
    public boolean canBeRemoved() {
        return this.currentVolume <= 0.001f && this.targetVolume <= 0.001f;
    }
}
