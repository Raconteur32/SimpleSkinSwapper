package fr.raconteur.simpleskinswapper

import fr.raconteur.simpleskinswapper.changeskin.IPlayerSkinUpdatable
import fr.raconteur.simpleskinswapper.networking.HandshakePayload
import fr.raconteur.simpleskinswapper.networking.SkinRefreshPayload
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SimpleSkinSwapper : ModInitializer {

    override fun onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SkinRefreshPayload.PACKET_ID, SkinRefreshPayload.PACKET_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.PACKET_ID, StreamCodec.unit(HandshakePayload))

        // Announce mod presence to client (enables direct packet path in singleplayer and Fabric servers)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            if (ServerPlayNetworking.canSend(player, HandshakePayload.PACKET_ID)) {
                ServerPlayNetworking.send(player, HandshakePayload)
            }
        }

        // Handle skin refresh packets sent by the client
        ServerPlayNetworking.registerGlobalReceiver(SkinRefreshPayload.PACKET_ID) { payload, context ->
            val player = context.player()
            LOGGER.info("Received SkinRefreshPayload from {}", player.name.string)
            context.server().execute {
                // Replace the GameProfile with a new one carrying the updated texture property.
                // GameProfile is a record in authlib 7+ so its PropertyMap may be immutable;
                // the mixin creates a fresh mutable PropertyMap instead of mutating the existing one.
                (player as IPlayerSkinUpdatable).`simpleSkinSwapper$setGameProfileWithTexture`(payload.textureProperty)

                // Broadcast updated tab list so all clients receive the new GameProfile with texture
                val playerList = context.server().playerList
                playerList.broadcastAll(ClientboundPlayerInfoRemovePacket(listOf(player.uuid)))
                playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(listOf(player)))
            }
        }

        LOGGER.info("SimpleSkinSwapper initializing...")
    }

    companion object {
        const val MOD_ID = "simpleskinswapper"

        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
