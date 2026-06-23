package de.ultrabuild.trainsounds.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TrainSoundVolumeConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("trainsounds-client.json");

    private static TrainSoundVolumeConfig config = new TrainSoundVolumeConfig();
    private static boolean loaded = false;

    private TrainSoundVolumeConfigManager() {
    }

    public static TrainSoundVolumeConfig getConfig() {
        ensureLoaded();
        return config;
    }

    public static float getVolumeMultiplier(String channel) {
        return getConfig().getVolumeMultiplier(channel);
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }

        loaded = true;
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            TrainSoundVolumeConfig loadedConfig = GSON.fromJson(reader, TrainSoundVolumeConfig.class);
            if (loadedConfig != null) {
                loadedConfig.clamp();
                config = loadedConfig;
            }
        } catch (IOException | JsonParseException ignored) {
            config = new TrainSoundVolumeConfig();
            save();
        }
    }

    public static void save() {
        config.clamp();

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
        }
    }
}