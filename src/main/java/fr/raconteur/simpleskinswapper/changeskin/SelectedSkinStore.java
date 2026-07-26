package fr.raconteur.simpleskinswapper.changeskin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import fr.raconteur.simpleskinswapper.gui.SkinType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelectedSkinStore {

    private static final Path FILE = FabricLoader.getInstance().getGameDir()
            .resolve("skins").resolve("selected.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Single-threaded so writes stay in submission order without blocking callers (e.g. the render thread).
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SimpleSkinSwapper-SkinStoreSave");
        t.setDaemon(true);
        return t;
    });

    private static volatile @Nullable Property selectedProperty = null;
    private static volatile boolean loaded = false;

    // In-memory only — for pause menu preview
    private static volatile @Nullable Identifier previewTextureId = null;
    private static volatile @Nullable SkinType previewSkinType = null;

    public static Optional<Property> get() {
        ensureLoaded();
        return Optional.ofNullable(selectedProperty);
    }

    public static void set(Property property) {
        selectedProperty = property;
        loaded = true;
        SAVE_EXECUTOR.submit(() -> save(property));
    }

    public static void setPreview(@Nullable Identifier textureId, @Nullable SkinType skinType) {
        previewTextureId = textureId;
        previewSkinType = skinType;
    }

    public static @Nullable Identifier getPreviewTexture() {
        return previewTextureId;
    }

    public static @Nullable SkinType getPreviewSkinType() {
        return previewSkinType;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            String content = Files.readString(FILE);
            JsonObject obj = GSON.fromJson(content, JsonObject.class);
            if (obj == null || !obj.has("value")) return;
            String value = obj.get("value").getAsString();
            String signature = obj.has("signature") && !obj.get("signature").isJsonNull()
                    ? obj.get("signature").getAsString() : null;
            selectedProperty = new Property("textures", value, signature);
            SimpleSkinSwapper.LOGGER.info("SelectedSkinStore: loaded selected skin.");
        } catch (Exception e) {
            SimpleSkinSwapper.LOGGER.warn("SelectedSkinStore: failed to load: {}", e.getMessage());
        }
    }

    private static void save(Property property) {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("value", property.value());
            if (property.hasSignature()) {
                obj.addProperty("signature", property.signature());
            } else {
                obj.add("signature", com.google.gson.JsonNull.INSTANCE);
            }
            Files.writeString(FILE, GSON.toJson(obj));
        } catch (IOException e) {
            SimpleSkinSwapper.LOGGER.warn("SelectedSkinStore: failed to save: {}", e.getMessage());
        }
    }
}
