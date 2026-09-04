package fr.raconteur.simpleskinswapper.changeskin

import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.networking.MineSkinUploader
import fr.raconteur.simpleskinswapper.networking.SkinShuffleCompat
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.impl.client.HttpClients
import java.io.File
import java.io.IOException
import java.util.function.Consumer

object SkinChange {

    @JvmStatic
    fun changeSkin(
        skinFile: File, skinType: SkinType,
        previewTextureId: Identifier?,
        onSuccess: Runnable, onError: Consumer<String>
    ) {
        val client = Minecraft.getInstance()

        SimpleSkinSwapper.LOGGER.info("changeSkin called for: {}", skinFile.name)

        val accessToken = getAccessToken(client)
        if (accessToken == null || accessToken == "0") {
            SimpleSkinSwapper.LOGGER.warn("No valid access token; cannot upload skin.")
            client.execute { onError.accept("No valid access token") }
            return
        }

        SimpleSkinSwapper.LOGGER.info("Access token OK, starting upload thread.")

        val thread = Thread({
            SimpleSkinSwapper.LOGGER.info("Uploading to Mojang...")
            try {
                HttpClients.createDefault().use { httpClient ->
                    val post = HttpPost("https://api.minecraftservices.com/minecraft/profile/skins")
                    post.addHeader("Authorization", "Bearer $accessToken")

                    val builder = MultipartEntityBuilder.create()
                    builder.addTextBody("variant", skinType.mojangVariant)
                    builder.addPart("file", FileBody(skinFile))
                    post.entity = builder.build()

                    val response = httpClient.execute(post)
                    val statusCode = response.statusLine.statusCode
                    SimpleSkinSwapper.LOGGER.info("Mojang upload HTTP {}", statusCode)

                    if (statusCode != 200) {
                        SimpleSkinSwapper.LOGGER.warn("Mojang upload failed (HTTP {}).", statusCode)
                    }
                }
            } catch (e: IOException) {
                SimpleSkinSwapper.LOGGER.warn("Mojang upload error: {}", e.message)
            }

            // Uploaded regardless of notification path, to get a canonical value+signature for selection tracking.
            SimpleSkinSwapper.LOGGER.info("Uploading to MineSkin proxy for texture Property...")
            val textureProperty = MineSkinUploader.upload(skinFile, skinType.mojangVariant)

            if (textureProperty != null) {
                client.execute {
                    SelectedSkinStore.set(textureProperty)
                    if (previewTextureId != null) {
                        SelectedSkinStore.setPreview(previewTextureId, skinType)
                    }
                }
            } else {
                SimpleSkinSwapper.LOGGER.warn("MineSkin upload failed; selection will not be persisted.")
            }

            SimpleSkinSwapper.LOGGER.info("SkinShuffle plugin detected: {}", SkinShuffleCompat.isInstalledOnServer())
            if (SkinShuffleCompat.isInstalledOnServer() && textureProperty != null) {
                SimpleSkinSwapper.LOGGER.info("Sending SkinRefreshPayload.")
                client.execute {
                    SkinShuffleCompat.sendSkinRefresh(textureProperty)
                    SkinSwapperState.endSwap()
                }
            } else {
                SimpleSkinSwapper.LOGGER.info("Using server command for skin notification.")
                SkinChangeManager.sendServerCommandIfNeeded()
            }

            SimpleSkinSwapper.LOGGER.info("Done, calling onSuccess.")
            client.execute(onSuccess)
        }, "SimpleSkinSwapper-SkinUpload")
        thread.isDaemon = true
        thread.start()
    }

    private fun getAccessToken(client: Minecraft): String? {
        return client.user.accessToken
    }
}
