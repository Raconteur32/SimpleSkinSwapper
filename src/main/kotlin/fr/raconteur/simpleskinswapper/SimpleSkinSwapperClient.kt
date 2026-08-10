package fr.raconteur.simpleskinswapper

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.changeskin.StartupSkinSync
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen
import fr.raconteur.simpleskinswapper.gui.SkinShuffleImporter
import fr.raconteur.simpleskinswapper.gui.SkinWheelScreen
import fr.raconteur.simpleskinswapper.networking.SkinShuffleCompat
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

class SimpleSkinSwapperClient : ClientModInitializer {

    override fun onInitializeClient() {
        SkinShuffleCompat.init()
        SkinShuffleImporter.importIfNeeded()
        StartupSkinSync.run()
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("simpleskinswapper", "title"))
        openCarouselKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.simpleskinswapper.open_carousel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
            )
        )

        openWheelKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.simpleskinswapper.open_wheel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
            )
        )

        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            client.currentServer?.let { server ->
                SimpleSkinSwapperConfig.get().registerServerIfAbsent(server.ip)
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            TOTAL_TICK_DELTA++
            if (openCarouselKey?.consumeClick() == true) {
                client.gui.setScreen(SkinCarouselScreen(client.gui.screen()))
            }
            if (openWheelKey?.consumeClick() == true) {
                client.gui.setScreen(SkinWheelScreen(client.gui.screen()))
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
    }
}
