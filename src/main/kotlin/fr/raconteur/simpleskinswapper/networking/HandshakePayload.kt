package fr.raconteur.simpleskinswapper.networking

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

object HandshakePayload : CustomPacketPayload {
    @JvmField
    val PACKET_ID: CustomPacketPayload.Type<HandshakePayload> =
        CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("skinshuffle", "handshake"))

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID
}
