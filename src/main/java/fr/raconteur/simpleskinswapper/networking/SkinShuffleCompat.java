package fr.raconteur.simpleskinswapper.networking;

import com.mojang.authlib.properties.Property;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class SkinShuffleCompat {
    /** True once a skinshuffle:handshake packet has been received from the server. */
    private static volatile boolean pluginPresent = false;

    /**
     * Set before sending SkinRefreshPayload; consumed by the afterPlayerList mixin
     * to invalidate the local player's cached PlayerInfo after the server broadcasts
     * the updated tab list entry.
     */
    private static volatile boolean awaitingSkinRefresh = false;

    public static boolean isInstalledOnServer() {
        return pluginPresent;
    }

    public static boolean consumeAwaitingSkinRefresh() {
        if (awaitingSkinRefresh) {
            awaitingSkinRefresh = false;
            return true;
        }
        return false;
    }

    public static void sendSkinRefresh(Property textureProperty) {
        awaitingSkinRefresh = true;
        ClientPlayNetworking.send(new SkinRefreshPayload(textureProperty));
    }

    public static void init() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> pluginPresent = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> pluginPresent = false);
        ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.PACKET_ID, (payload, context) -> {
            pluginPresent = true;
            SimpleSkinSwapper.LOGGER.info("SkinShuffle Bridge plugin detected on this server.");
        });
    }
}
