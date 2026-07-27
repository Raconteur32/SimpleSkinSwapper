package fr.raconteur.simpleskinswapper.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SkinTypeStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path typesFile() {
        return FabricLoader.getInstance().getGameDir().resolve("skins").resolve("types.json");
    }

    private static Map<String, String> load() {
        Path path = typesFile();
        if (!Files.exists(path)) return new HashMap<>();
        try {
            String json = Files.readString(path);
            Map<String, String> map = GSON.fromJson(json, MAP_TYPE);
            return map != null ? map : new HashMap<>();
        } catch (IOException e) {
            SimpleSkinSwapper.LOGGER.warn("Could not read types.json: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private static void save(Map<String, String> map) {
        Path path = typesFile();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(map));
        } catch (IOException e) {
            SimpleSkinSwapper.LOGGER.warn("Could not write types.json: {}", e.getMessage());
        }
    }

    /** Returns the stored type for a skin file, falling back to the auto-detected one. */
    public static SkinType getType(String filename, SkinType detected) {
        String stored = load().get(filename);
        if (stored == null) {
            // First access: persist the detected value
            setType(filename, detected);
            return detected;
        }
        return "slim".equals(stored) ? SkinType.SLIM : SkinType.CLASSIC;
    }

    /** Stores the user-chosen type for a skin file. */
    public static void setType(String filename, SkinType type) {
        Map<String, String> map = load();
        map.put(filename, type.getMojangVariant());
        save(map);
    }

    /** Removes the stored type for a skin file, e.g. when the file is deleted. */
    public static void removeType(String filename) {
        Map<String, String> map = load();
        if (map.remove(filename) != null) {
            save(map);
        }
    }
}
