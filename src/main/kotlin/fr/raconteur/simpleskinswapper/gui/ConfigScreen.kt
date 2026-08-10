package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class ConfigScreen(private val parent: Screen?) : Screen(Component.translatable("simpleskinswapper.config.title")) {

    /** Null when not connected to a multiplayer server. */
    private val currentServerAddress: String? =
        Minecraft.getInstance().currentServer?.ip

    private lateinit var serverCommandField: EditBox

    override fun init() {
        val config = SimpleSkinSwapperConfig.get()

        val centerX = this.width / 2
        val startY = this.height / 4

        // Title
        this.addRenderableWidget(
            MultiLineTextWidget(centerX - 150, 14, this.title, this.font)
                .setMaxWidth(300)
                .setCentered(true)
        )

        if (currentServerAddress != null) {
            // Label: "Command for: example.com"
            this.addRenderableWidget(
                MultiLineTextWidget(
                    centerX - 150, startY,
                    Component.translatable("simpleskinswapper.config.server_command.for", currentServerAddress),
                    this.font
                )
                    .setMaxWidth(300)
                    .setCentered(true)
            )

            // Command input field
            this.serverCommandField = EditBox(
                this.font, centerX - 150, startY + 20, 300, 20,
                Component.translatable("simpleskinswapper.config.server_command")
            )
            val existing = config.getCommandForServer(currentServerAddress)
            this.serverCommandField.value = existing ?: ""
            this.serverCommandField.setMaxLength(256)
            this.addRenderableWidget(this.serverCommandField)
        } else {
            // Not connected — explain the feature
            val notConnectedText = Component.translatable("simpleskinswapper.config.server_command.not_connected.line1")
                .append("\n")
                .append(Component.translatable("simpleskinswapper.config.server_command.not_connected.line2"))
                .append("\n")
                .append(Component.translatable("simpleskinswapper.config.server_command.not_connected.line3"))
                .append("\n")
                .append(Component.translatable("simpleskinswapper.config.server_command.not_connected.line4"))
            this.addRenderableWidget(
                MultiLineTextWidget(centerX - 150, startY, notConnectedText, this.font)
                    .setMaxWidth(300)
                    .setCentered(true)
            )
        }

        // Save button
        this.addRenderableWidget(
            Button.builder(
                Component.translatable("simpleskinswapper.config.save")
            ) {
                if (currentServerAddress != null) save()
                this.minecraft?.gui?.setScreen(parent)
            }
                .bounds(centerX - 100, this.height - 30, 95, 20)
                .build()
        )

        // Cancel button
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) {
                this.minecraft?.gui?.setScreen(parent)
            }
                .bounds(centerX + 5, this.height - 30, 95, 20)
                .build()
        )
    }

    private fun save() {
        val config = SimpleSkinSwapperConfig.get()
        currentServerAddress?.let { config.serverCommands?.put(it, this.serverCommandField.value) }
        SimpleSkinSwapperConfig.save()
    }

    override fun onClose() {
        this.minecraft?.gui?.setScreen(parent)
    }
}
