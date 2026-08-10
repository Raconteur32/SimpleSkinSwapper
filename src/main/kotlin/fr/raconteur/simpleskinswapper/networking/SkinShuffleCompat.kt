package fr.raconteur.simpleskinswapper.networking

import com.mojang.authlib.properties.Property
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

object SkinShuffleCompat {
    /** True once a skinshuffle:handshake packet has been received from the server. */
    @Volatile
    private var pluginPresent = false

    /**
     * Set before sending SkinRefreshPayload; consumed by the afterPlayerList mixin
     * to invalidate the local player's cached PlayerInfo after the server broadcasts
     * the updated tab list entry.
     */
    @Volatile
    private var awaitingSkinRefresh = false

    @JvmStatic
    fun isInstalledOnServer(): Boolean = pluginPresent

    @JvmStatic
    fun consumeAwaitingSkinRefresh(): Boolean {
        if (awaitingSkinRefresh) {
            awaitingSkinRefresh = false
            return true
        }
        return false
    }

    @JvmStatic
    fun sendSkinRefresh(textureProperty: Property) {
        awaitingSkinRefresh = true
        ClientPlayNetworking.send(SkinRefreshPayload(textureProperty))
    }

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.INIT.register { _, _ -> pluginPresent = false }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> pluginPresent = false }
        ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.PACKET_ID) { _, _ ->
            pluginPresent = true
            SimpleSkinSwapper.LOGGER.info("SkinShuffle Bridge plugin detected on this server.")
        }
    }
}
