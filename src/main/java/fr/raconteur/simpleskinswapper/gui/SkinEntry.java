package fr.raconteur.simpleskinswapper.gui;

import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a skin file entry in the carousel.
 * Lazily loads the GPU texture on first render.
 */
public class SkinEntry {

    public final File file;
    public final String displayName;
    public SkinType skinType;

    /** GPU texture identifier, null until loaded. */
    public Identifier textureId = null;
    public boolean textureLoading = false;

    public SkinEntry(File file) {
        this.file = file;
        SkinType detected = SkinUtils.detectSkinType(file);
        this.skinType = SkinTypeStore.getType(file.getName(), detected);

        // Display name: filename without extension
        String name = file.getName();
        this.displayName = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
    }

    /**
     * Scan the game's skins/ directory and return all .png files as SkinEntry objects.
     */
    public static List<SkinEntry> loadSkins() {
        Path skinsDir = FabricLoader.getInstance().getGameDir().resolve("skins");
        File dir = skinsDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        List<SkinEntry> entries = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                entries.add(new SkinEntry(file));
            }
        }
        return entries;
    }

    /**
     * Ensure the GPU texture is loaded. Call from render thread.
     * No-op if already loading or loaded.
     */
    public void ensureTextureLoaded() {
        if (textureId != null || textureLoading) return;
        textureLoading = true;

        String sanitized = file.getName()
                .toLowerCase()
                .replaceAll("[^a-z0-9_/.-]", "_");
        String pathHash = String.format("%08x", file.getAbsolutePath().hashCode() & 0x7FFFFFFF);
        String key = "skin/entry_" + sanitized + "_" + pathHash;

        SkinUtils.loadSkinTextureAsync(file, key, id -> {
            this.textureId = id;
            SimpleSkinSwapper.LOGGER.debug("Loaded skin entry texture: {}", file.getName());
        });
    }
}
