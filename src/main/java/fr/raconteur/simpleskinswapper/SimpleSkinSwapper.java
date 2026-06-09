package fr.raconteur.simpleskinswapper;

import fr.raconteur.simpleskinswapper.changeskin.IPlayerSkinUpdatable;
import fr.raconteur.simpleskinswapper.networking.HandshakePayload;
import fr.raconteur.simpleskinswapper.networking.SkinRefreshPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;

public class SimpleSkinSwapper implements ModInitializer {

    public static final String MOD_ID = "simpleskinswapper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(SkinRefreshPayload.PACKET_ID, SkinRefreshPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(HandshakePayload.PACKET_ID, PacketCodec.unit(HandshakePayload.INSTANCE));

        // Announce mod presence to client (enables direct packet path in singleplayer and Fabric servers)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (ServerPlayNetworking.canSend(player, HandshakePayload.PACKET_ID)) {
                ServerPlayNetworking.send(player, HandshakePayload.INSTANCE);
            }
        });

        // Handle skin refresh packets sent by the client
        ServerPlayNetworking.registerGlobalReceiver(SkinRefreshPayload.PACKET_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            LOGGER.info("Received SkinRefreshPayload from {}", player.getName().getString());
            context.server().execute(() -> {
                ((IPlayerSkinUpdatable) player).simpleSkinSwapper$setGameProfileWithTexture(payload.textureProperty());

                var playerManager = context.server().getPlayerManager();
                playerManager.sendToAll(new PlayerRemoveS2CPacket(new ArrayList<>(Collections.singleton(player.getUuid()))));
                playerManager.sendToAll(PlayerListS2CPacket.entryFromPlayer(Collections.singleton(player)));
            });
        });

        LOGGER.info("SimpleSkinSwapper initializing...");
    }
}
