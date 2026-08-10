package fr.raconteur.simpleskinswapper

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component

/**
 * Cross-version player chat notifications.
 * Mojang renamed displayClientMessage to sendSystemMessage/sendOverlayMessage in 26.x.
 */
fun LocalPlayer.systemMessage(text: Component) {
    //? if >=26.1 {
    sendSystemMessage(text)
    //?} else {
    /*displayClientMessage(text, false)
    *///?}
}

fun LocalPlayer.overlayMessage(text: Component) {
    //? if >=26.1 {
    sendOverlayMessage(text)
    //?} else {
    /*displayClientMessage(text, true)
    *///?}
}
