package com.fartiqs.motionblur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fartiqsmotionblur.json");

    public boolean enabled = true;
    public float strength = 0.60f;        // 0.0 - 0.95
    public boolean adaptive = false;      // blur harder when turning fast
    public float adaptiveSensitivity = 0.5f; // 0 - 1
    public boolean disableInMenus = false;
    public boolean aspectEnabled = false;
    public float aspect = 16f / 9f;       // 0.5 - 4.0
    public int aspectPreset = 2;          // index into ASPECT_PRESETS, last = custom

    public static final String[] ASPECT_NAMES = {"4:3", "5:4", "16:9", "16:10", "21:9", "32:9", "1:1", "Custom"};
    public static final float[] ASPECT_VALUES = {4f / 3f, 5f / 4f, 16f / 9f, 16f / 10f, 21f / 9f, 32f / 9f, 1f, -1f};

    private static Config instance = new Config();

    public static Config get() { return instance; }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reset() {
        Config d = new Config();
        enabled = d.enabled; strength = d.strength; adaptive = d.adaptive;
        adaptiveSensitivity = d.adaptiveSensitivity; disableInMenus = d.disableInMenus;
        aspectEnabled = d.aspectEnabled; aspect = d.aspect; aspectPreset = d.aspectPreset;
    }
}
