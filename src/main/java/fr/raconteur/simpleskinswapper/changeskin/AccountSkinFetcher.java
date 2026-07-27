package fr.raconteur.simpleskinswapper.changeskin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fetches a player's current skin from Mojang by account username and saves it into skins/.
 */
public class AccountSkinFetcher {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * Fetches the given account's current skin and writes it to {@code destination}.
     * The destination is chosen by the caller upfront so it can track the file before
     * the write happens (e.g. to tell a directory watcher to ignore its own creation event).
     */
    public static void fetch(String username, Path destination, Consumer<File> onSuccess, Runnable onFailure) {
        Thread thread = new Thread(() -> run(username, destination, onSuccess, onFailure), "SimpleSkinSwapper-AccountFetch");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(String username, Path destination, Consumer<File> onSuccess, Runnable onFailure) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            UUID uuid = fetchUuid(username);
            if (uuid == null) {
                client.execute(onFailure);
                return;
            }

            Property property = StartupSkinSync.fetchMojangProperty(uuid);
            if (property == null) {
                client.execute(onFailure);
                return;
            }

            String skinUrl = StartupSkinSync.extractSkinUrl(property.value());
            if (skinUrl == null) {
                client.execute(onFailure);
                return;
            }

            byte[] skinBytes = StartupSkinSync.downloadUrl(skinUrl);
            if (skinBytes == null) {
                client.execute(onFailure);
                return;
            }

            Files.createDirectories(destination.getParent());
            Files.write(destination, skinBytes);

            File file = destination.toFile();
            client.execute(() -> onSuccess.accept(file));
        } catch (Exception e) {
            SimpleSkinSwapper.LOGGER.warn("AccountSkinFetcher failed: {}", e.getMessage());
            client.execute(onFailure);
        }
    }

    @Nullable
    private static UUID fetchUuid(String username) {
        try {
            URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonObject body = GSON.fromJson(resp.body(), JsonObject.class);
            if (body == null || !body.has("id")) return null;
            String raw = body.get("id").getAsString();
            return UUID.fromString(raw.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            SimpleSkinSwapper.LOGGER.warn("AccountSkinFetcher: username lookup failed: {}", e.getMessage());
            return null;
        }
    }

    public static String sanitizeFilename(String username) {
        return username.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public static Path uniqueFile(Path path) {
        if (!Files.exists(path)) return path;
        String name = path.getFileName().toString();
        String base = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        Path dir = path.getParent();
        int i = 2;
        Path candidate;
        do {
            candidate = dir.resolve(base + "-" + i + ".png");
            i++;
        } while (Files.exists(candidate));
        return candidate;
    }
}
