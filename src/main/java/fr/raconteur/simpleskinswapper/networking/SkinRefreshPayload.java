package fr.raconteur.simpleskinswapper.networking;

import com.mojang.authlib.properties.Property;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SkinRefreshPayload(Property textureProperty) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkinRefreshPayload> PACKET_ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("skinshuffle", "skin_refresh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkinRefreshPayload> PACKET_CODEC =
            StreamCodec.ofMember(SkinRefreshPayload::write, SkinRefreshPayload::read);

    private static void write(SkinRefreshPayload payload, RegistryFriendlyByteBuf buf) {
        Property p = payload.textureProperty();
        buf.writeBoolean(p.hasSignature());
        buf.writeUtf(p.name());
        buf.writeUtf(p.value());
        if (p.hasSignature()) buf.writeUtf(p.signature());
    }

    private static SkinRefreshPayload read(RegistryFriendlyByteBuf buf) {
        boolean hasSig = buf.readBoolean();
        String name = buf.readUtf();
        String value = buf.readUtf();
        String sig = hasSig ? buf.readUtf() : null;
        return new SkinRefreshPayload(new Property(name, value, sig));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
