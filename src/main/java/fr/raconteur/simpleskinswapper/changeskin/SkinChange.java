package fr.raconteur.simpleskinswapper.changeskin;

import com.mojang.authlib.properties.Property;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import fr.raconteur.simpleskinswapper.gui.SkinType;
import fr.raconteur.simpleskinswapper.networking.MineSkinUploader;
import fr.raconteur.simpleskinswapper.networking.SkinShuffleCompat;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public class SkinChange {

    /**
     * Apply a skin. Uploads to Mojang if a valid access token is available.
     * If a SkinShuffle-compatible plugin is detected, sends a skin refresh packet
     * via the MineSkin proxy instead of a server command.
     *
     * Callbacks always execute on the render thread.
     *
     * @param skinFile  The PNG file to apply.
     * @param skinType  CLASSIC or SLIM.
     * @param onSuccess Called when done.
     * @param onError   Called on failure.
     */
    public static void changeSkin(File skinFile, SkinType skinType,
                                   Runnable onSuccess, Consumer<String> onError) {
        Minecraft client = Minecraft.getInstance();

        SimpleSkinSwapper.LOGGER.info("changeSkin called for: {}", skinFile.getName());

        String accessToken = getAccessToken(client);
        if (accessToken == null || accessToken.equals("0")) {
            SimpleSkinSwapper.LOGGER.warn("No valid access token; cannot upload skin.");
            client.execute(() -> onError.accept("No valid access token"));
            return;
        }

        SimpleSkinSwapper.LOGGER.info("Access token OK, starting upload thread.");

        Thread thread = new Thread(() -> {
            SimpleSkinSwapper.LOGGER.info("Uploading to Mojang...");
            // Upload to Mojang for permanent skin change
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("https://api.minecraftservices.com/minecraft/profile/skins");
                post.addHeader("Authorization", "Bearer " + accessToken);

                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("variant", skinType.getMojangVariant());
                builder.addPart("file", new FileBody(skinFile));
                post.setEntity(builder.build());

                HttpResponse response = httpClient.execute(post);
                int statusCode = response.getStatusLine().getStatusCode();
                SimpleSkinSwapper.LOGGER.info("Mojang upload HTTP {}", statusCode);

                if (statusCode != 200) {
                    SimpleSkinSwapper.LOGGER.warn("Mojang upload failed (HTTP {}).", statusCode);
                }
            } catch (Exception e) {
                SimpleSkinSwapper.LOGGER.warn("Mojang upload error: {}", e.getMessage());
            }

            // Notify the server of the skin change
            SimpleSkinSwapper.LOGGER.info("SkinShuffle plugin detected: {}", SkinShuffleCompat.isInstalledOnServer());
            if (SkinShuffleCompat.isInstalledOnServer()) {
                SimpleSkinSwapper.LOGGER.info("Uploading to MineSkin proxy...");
                Property textureProperty = MineSkinUploader.upload(skinFile, skinType.getMojangVariant());
                if (textureProperty != null) {
                    SimpleSkinSwapper.LOGGER.info("MineSkin upload OK, sending SkinRefreshPayload.");
                    client.execute(() -> {
                        SkinShuffleCompat.sendSkinRefresh(textureProperty);
                        SkinSwapperState.endSwap();
                    });
                } else {
                    SimpleSkinSwapper.LOGGER.warn("MineSkin upload failed, falling back to server command.");
                    SkinChangeManager.sendServerCommandIfNeeded();
                }
            } else {
                SimpleSkinSwapper.LOGGER.info("No plugin detected, using server command.");
                SkinChangeManager.sendServerCommandIfNeeded();
            }

            SimpleSkinSwapper.LOGGER.info("Done, calling onSuccess.");
            client.execute(onSuccess);
        }, "SimpleSkinSwapper-SkinUpload");
        thread.setDaemon(true);
        thread.start();
    }

    private static String getAccessToken(Minecraft client) {
        if (client.getUser() == null) return null;
        return client.getUser().getAccessToken();
    }
}
