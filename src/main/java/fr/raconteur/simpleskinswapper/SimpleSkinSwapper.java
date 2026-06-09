package fr.raconteur.simpleskinswapper;

import fr.raconteur.simpleskinswapper.changeskin.IPlayerSkinUpdatable;
import fr.raconteur.simpleskinswapper.networking.HandshakePayload;
import fr.raconteur.simpleskinswapper.networking.SkinRefreshPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SimpleSkinSwapper implements ModInitializer {

    public static final String MOD_ID = "simpleskinswapper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SkinRefreshPayload.PACKET_ID, SkinRefreshPayload.PACKET_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.PACKET_ID, StreamCodec.unit(HandshakePayload.INSTANCE));

        // Announce mod presence to client (enables direct packet path in singleplayer and Fabric servers)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (ServerPlayNetworking.canSend(player, HandshakePayload.PACKET_ID)) {
                ServerPlayNetworking.send(player, HandshakePayload.INSTANCE);
            }
        });

        // Handle skin refresh packets sent by the client
        ServerPlayNetworking.registerGlobalReceiver(SkinRefreshPayload.PACKET_ID, (payload, context) -> {
            ServerPlayer player = context.player();
            LOGGER.info("Received SkinRefreshPayload from {}", player.getName().getString());
            context.server().execute(() -> {
                // Replace the GameProfile with a new one carrying the updated texture property.
                // GameProfile is a record in authlib 7+ so its PropertyMap may be immutable;
                // the mixin creates a fresh mutable PropertyMap instead of mutating the existing one.
                ((IPlayerSkinUpdatable) player).simpleSkinSwapper$setGameProfileWithTexture(payload.textureProperty());

                // Broadcast updated tab list so all clients receive the new GameProfile with texture
                var playerList = context.server().getPlayerList();
                playerList.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
                playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
            });
        });

        LOGGER.info("SimpleSkinSwapper initializing...");
    }
}
