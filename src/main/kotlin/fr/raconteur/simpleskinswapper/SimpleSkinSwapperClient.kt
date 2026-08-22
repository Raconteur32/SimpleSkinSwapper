package fr.raconteur.simpleskinswapper

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.changeskin.StartupSkinSync
import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen
import fr.raconteur.simpleskinswapper.gui.SkinShuffleImporter
import fr.raconteur.simpleskinswapper.gui.SkinWheelScreen
import fr.raconteur.simpleskinswapper.networking.SkinShuffleCompat
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

class SimpleSkinSwapperClient : ClientModInitializer {

    override fun onInitializeClient() {
        SkinShuffleCompat.init()
        SkinShuffleImporter.importIfNeeded()
        StartupSkinSync.run()
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("simpleskinswapper", "title"))
        //? if >=26.3 {
        /*val unboundKeyType = InputConstants.Type.KEYBOARD
        *///?} else {
        val unboundKeyType = InputConstants.Type.KEYSYM
        //?}
        openCarouselKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.simpleskinswapper.open_carousel",
                unboundKeyType,
                InputConstants.UNKNOWN.value,
                category
            )
        )

        openWheelKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.simpleskinswapper.open_wheel",
                unboundKeyType,
                InputConstants.UNKNOWN.value,
                category
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            TOTAL_TICK_DELTA++
            // TEMP-DEBUG: auto-open the YACL config screen for automated GUI testing
            //? if >=26.2 {
            if (debugAutoOpenConfig && client.gui.screen() is net.minecraft.client.gui.screens.TitleScreen) {
            //?} else {
            /*if (debugAutoOpenConfig && client.screen is net.minecraft.client.gui.screens.TitleScreen) {
            *///?}
                debugAutoOpenConfig = false
                //? if >=26.2 {
                client.gui.setScreen(fr.raconteur.simpleskinswapper.gui.config.YaclConfigScreen.create(null))
                //?} else {
                /*client.setScreen(fr.raconteur.simpleskinswapper.gui.config.YaclConfigScreen.create(null))
                *///?}
            }
            if (openCarouselKey?.consumeClick() == true) {
                //? if >=26.2 {
                client.gui.setScreen(SkinCarouselScreen(client.gui.screen()))
                //?} else {
                /*client.setScreen(SkinCarouselScreen(client.screen))
                *///?}
            }
            if (openWheelKey?.consumeClick() == true) {
                //? if >=26.2 {
                client.gui.setScreen(SkinWheelScreen(client.gui.screen()))
                //?} else {
                /*client.setScreen(SkinWheelScreen(client.screen))
                *///?}
            }
        }
    }

    companion object {
        @JvmField
        var openCarouselKey: KeyMapping? = null

        @JvmField
        var openWheelKey: KeyMapping? = null

        /** Accumulated tick delta for animations (incremented each game tick). */
        @JvmField
        var TOTAL_TICK_DELTA = 0f

        // TEMP-DEBUG: set by -Dsss.debugConfig=1 for automated GUI testing
        @JvmField
        var debugAutoOpenConfig: Boolean = System.getProperty("sss.debugConfig") != null
    }
}
