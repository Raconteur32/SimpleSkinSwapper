package fr.raconteur.simpleskinswapper;

import fr.raconteur.simpleskinswapper.networking.HandshakePayload;
import fr.raconteur.simpleskinswapper.networking.SkinRefreshPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.StreamCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleSkinSwapper implements ModInitializer {

    public static final String MOD_ID = "simpleskinswapper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SkinRefreshPayload.PACKET_ID, SkinRefreshPayload.PACKET_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.PACKET_ID, StreamCodec.unit(HandshakePayload.INSTANCE));
        LOGGER.info("SimpleSkinSwapper initializing...");
    }
}
