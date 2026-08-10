package fr.raconteur.simpleskinswapper.networking

import com.mojang.authlib.properties.Property
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class SkinRefreshPayload(val textureProperty: Property) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    private fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(textureProperty.hasSignature())
        buf.writeUtf(textureProperty.name())
        buf.writeUtf(textureProperty.value())
        if (textureProperty.hasSignature()) buf.writeUtf(textureProperty.signature()!!)
    }

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<SkinRefreshPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("skinshuffle", "skin_refresh"))

        @JvmField
        val PACKET_CODEC: StreamCodec<RegistryFriendlyByteBuf, SkinRefreshPayload> =
            StreamCodec.ofMember(SkinRefreshPayload::write, Companion::read)

        private fun read(buf: RegistryFriendlyByteBuf): SkinRefreshPayload {
            val hasSig = buf.readBoolean()
            val name = buf.readUtf()
            val value = buf.readUtf()
            val sig = if (hasSig) buf.readUtf() else null
            return SkinRefreshPayload(Property(name, value, sig))
        }
    }
}
