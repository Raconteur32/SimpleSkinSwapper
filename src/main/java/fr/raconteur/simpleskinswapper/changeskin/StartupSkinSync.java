package fr.raconteur.simpleskinswapper.changeskin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import fr.raconteur.simpleskinswapper.gui.SkinEntry;
import fr.raconteur.simpleskinswapper.gui.SkinType;
import fr.raconteur.simpleskinswapper.gui.SkinUtils;
import fr.raconteur.simpleskinswapper.networking.MineSkinCache;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StartupSkinSync {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void run() {
        Thread thread = new Thread(StartupSkinSync::sync, "SimpleSkinSwapper-StartupSync");
        thread.setDaemon(true);
        thread.start();
    }

    private static void sync() {
        try {
            Minecraft client = Minecraft.getInstance();
            UUID uuid = client.getUser().getProfileId();
            if (uuid == null) {
                SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: no UUID available, skipping.");
                return;
            }

            Property mojangProperty = fetchMojangProperty(uuid);
            if (mojangProperty == null) return;

            String mojangUrl = extractSkinUrl(mojangProperty.value());
            if (mojangUrl == null) {
                SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: could not extract skin URL from Mojang response.");
                return;
            }

            Optional<Property> stored = SelectedSkinStore.get();
            boolean matchesStored = false;
            if (stored.isPresent()) {
                String storedUrl = extractSkinUrl(stored.get().value());
                matchesStored = mojangUrl.equals(storedUrl);
            }

            if (!matchesStored) {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: skin mismatch, updating stored selection.");
                SelectedSkinStore.set(mojangProperty);
            } else {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: stored selection matches Mojang skin.");
            }

            // Preview texture is never persisted, so it must be reloaded from a matching local file every launch.
            SkinType mojangSkinType = extractSkinType(mojangProperty.value());
            SkinEntry matchingEntry = findMatchingEntry(mojangUrl);

            if (matchingEntry != null) {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: matched existing file {}.", matchingEntry.displayName);
                final SkinType skinType = matchingEntry.skinType;
                final SkinEntry entry = matchingEntry;
                SkinUtils.loadSkinTextureAsync(entry.file, "selected_preview", id ->
                        SelectedSkinStore.setPreview(id, skinType));
                return;
            }

            if (matchesStored) {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: no local file to preview, skipping download.");
                return;
            }

            byte[] skinBytes = downloadUrl(mojangUrl);
            if (skinBytes == null) return;

            String pseudo = client.getUser().getName();
            String timestamp = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
            Path skinsDir = FabricLoader.getInstance().getGameDir().resolve("skins");
            Files.createDirectories(skinsDir);
            Path outFile = skinsDir.resolve(pseudo + "-" + timestamp + ".png");
            Files.write(outFile, skinBytes);
            SimpleSkinSwapper.LOGGER.info("StartupSkinSync: saved Mojang skin as {}.", outFile.getFileName());

            String hash = MineSkinCache.fileHash(outFile.toFile());
            if (hash != null) {
                MineSkinCache.put(mojangSkinType.getMojangVariant() + "_" + hash, mojangProperty);
            }

            SkinUtils.loadSkinTextureAsync(outFile.toFile(), "selected_preview", id ->
                    SelectedSkinStore.setPreview(id, mojangSkinType));

        } catch (Exception e) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync failed: {}", e.getMessage());
        }
    }

    @Nullable
    static Property fetchMojangProperty(UUID uuid) {
        try {
            String uuidStr = uuid.toString().replace("-", "");
            URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false");
            HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: Mojang profile HTTP {}.", resp.statusCode());
                return null;
            }
            JsonObject body = GSON.fromJson(resp.body(), JsonObject.class);
            if (!body.has("properties")) return null;
            for (var el : body.getAsJsonArray("properties")) {
                JsonObject prop = el.getAsJsonObject();
                if ("textures".equals(prop.get("name").getAsString())) {
                    String value = prop.get("value").getAsString();
                    String sig = prop.has("signature") && !prop.get("signature").isJsonNull()
                            ? prop.get("signature").getAsString() : null;
                    return new Property("textures", value, sig);
                }
            }
        } catch (Exception e) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: fetchMojangProperty failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Decode the base64 texture value and extract the skin texture URL.
     * The URL is content-addressed on Mojang's CDN, so identical PNGs share the same URL.
     */
    @Nullable
    public static String extractSkinUrl(String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value));
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            return obj.getAsJsonObject("textures")
                    .getAsJsonObject("SKIN")
                    .get("url").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static SkinType extractSkinType(String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value));
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            JsonObject skin = obj.getAsJsonObject("textures").getAsJsonObject("SKIN");
            if (skin.has("metadata")) {
                String model = skin.getAsJsonObject("metadata").get("model").getAsString();
                if ("slim".equals(model)) return SkinType.SLIM;
            }
        } catch (Exception ignored) {}
        return SkinType.CLASSIC;
    }

    @Nullable
    private static SkinEntry findMatchingEntry(String mojangUrl) {
        List<SkinEntry> entries = SkinEntry.loadSkins();
        for (SkinEntry entry : entries) {
            String hash = MineSkinCache.fileHash(entry.file);
            if (hash == null) continue;
            for (String variant : List.of("classic", "slim")) {
                Property cached = MineSkinCache.get(variant + "_" + hash);
                if (cached == null) continue;
                String cachedUrl = extractSkinUrl(cached.value());
                if (mojangUrl.equals(cachedUrl)) return entry;
            }
        }
        return null;
    }

    @Nullable
    static byte[] downloadUrl(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) return resp.body();
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: download HTTP {}.", resp.statusCode());
        } catch (Exception e) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: download failed: {}", e.getMessage());
        }
        return null;
    }
}
