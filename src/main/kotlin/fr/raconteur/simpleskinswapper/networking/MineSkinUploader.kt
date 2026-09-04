package fr.raconteur.simpleskinswapper.networking

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mojang.authlib.properties.Property
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Duration
import java.util.Optional
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object MineSkinUploader {
    private const val PROXY_HOST = "sssmineskinsproxy.raconteur.fr:28433"
    private val PROXY_URI: URI = URI.create("ws://$PROXY_HOST/skin-gateway")
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Inbound gateway response: {"textureValue":...,"textureSignature":...}. */
    @Serializable
    private data class GatewayResponseDto(val textureValue: String? = null, val textureSignature: String? = null)
    private val HTTP_CLIENT: HttpClient = HttpClient.newHttpClient()

    /**
     * Uploads the skin file via WebSocket to the MineSkin proxy and returns
     * a texture Property (value + signature), or null on failure.
     */
    // Deliberate total guard: connect, queue wait and future resolution must never
    // crash the upload thread; log and return null.
    @Suppress("TooGenericExceptionCaught")
    @JvmStatic
    fun upload(skinFile: File, variant: String): Property? {
        val fileHash = MineSkinCache.fileHash(skinFile)
        val cacheKey = if (fileHash != null) "${variant}_$fileHash" else null
        if (cacheKey != null) {
            val cached = MineSkinCache.get(cacheKey)
            if (cached != null) return cached
        }

        try {
            val fileBytes = Files.readAllBytes(skinFile.toPath())
            SimpleSkinSwapper.LOGGER.info("MineSkin: uploading {} ({} bytes)", skinFile.name, fileBytes.size)

            val req = JsonObject()
            req.addProperty("type", "file")
            req.addProperty("model", variant)
            val jsonMessage = Gson().toJson(req)

            val channel = LinkedBlockingQueue<Optional<Property>>(1)

            HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(PROXY_URI, UploadListener(fileBytes, jsonMessage, channel))
                .exceptionally { e ->
                    SimpleSkinSwapper.LOGGER.warn("MineSkin: connection failed: {}", e.message)
                    channel.offer(Optional.empty())
                    null
                }

            SimpleSkinSwapper.LOGGER.info("MineSkin: waiting for response (timeout 30s)...")
            val outcome = channel.poll(30, TimeUnit.SECONDS)
            if (outcome == null) {
                SimpleSkinSwapper.LOGGER.warn("MineSkin: timed out waiting for response")
                return null
            }
            if (outcome.isEmpty) return null

            val prop = outcome.get()
            if (cacheKey != null) MineSkinCache.put(cacheKey, prop)
            return prop
        } catch (e: Exception) {
            SimpleSkinSwapper.LOGGER.warn("MineSkin: upload failed: {}", e.message)
            return null
        }
    }

    private class UploadListener(
        private val fileBytes: ByteArray,
        private val jsonMessage: String,
        private val channel: BlockingQueue<Optional<Property>>
    ) : WebSocket.Listener {
        override fun onOpen(ws: WebSocket) {
            SimpleSkinSwapper.LOGGER.info("MineSkin: connected, sending skin data then JSON")
            ws.sendBinary(ByteBuffer.wrap(fileBytes), true)
                .thenRun { ws.sendText(jsonMessage, true) }
            ws.request(1)
        }

        override fun onText(ws: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*>? {
            SimpleSkinSwapper.LOGGER.info("MineSkin: received response: {}", data)
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            try {
                val body = json.decodeFromString(GatewayResponseDto.serializer(), data.toString())
                val value = body.textureValue
                if (value == null) {
                    SimpleSkinSwapper.LOGGER.warn("MineSkin: response has no textureValue")
                    channel.offer(Optional.empty())
                    return null
                }
                SimpleSkinSwapper.LOGGER.info(
                    "MineSkin: texture property parsed OK (signature: {})", body.textureSignature != null
                )
                channel.offer(Optional.of(Property("textures", value, body.textureSignature)))
            } catch (e: SerializationException) {
                SimpleSkinSwapper.LOGGER.warn("MineSkin: could not parse response", e)
                channel.offer(Optional.empty())
            }
            return null
        }

        override fun onError(ws: WebSocket, err: Throwable) {
            SimpleSkinSwapper.LOGGER.warn("MineSkin: WebSocket error: {}", err.message)
            channel.offer(Optional.empty())
        }
    }
}
