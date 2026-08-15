package fr.raconteur.simpleskinswapper.gui.config

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.YetAnotherConfigLib
import fr.raconteur.simpleskinswapper.config.ServerCommand
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Builds the YACL config screen: an always-accessible, editable list of
 * per-server skin commands. The Gson config stays the source of truth;
 * edits are staged in [staged] and persisted on save.
 */
object YaclConfigScreen {

    fun create(parent: Screen?): Screen {
        val config = SimpleSkinSwapperConfig.get()
        val currentServerAddress = Minecraft.getInstance().currentServer?.ip
        var staged = config.toServerCommandList(currentServerAddress)

        return YetAnotherConfigLib.createBuilder()
            .title(Component.translatable("simpleskinswapper.config.title"))
            .category(
                ConfigCategory.createBuilder()
                    .name(Component.translatable("simpleskinswapper.config.category.servers"))
                    .option(
                        ListOption.createBuilder<ServerCommand>()
                            .name(Component.translatable("simpleskinswapper.config.server_commands"))
                            .description(
                                OptionDescription.of(
                                    Component.translatable("simpleskinswapper.config.server_commands.description")
                                )
                            )
                            .binding(
                                emptyList(),
                                { staged },
                                { newList -> staged = newList.toMutableList() }
                            )
                            .controller { option -> ServerCommandControllerBuilder.create(option) }
                            .initial(ServerCommand("", ""))
                            .build()
                    )
                    .build()
            )
            .save {
                config.applyServerCommandList(staged)
                SimpleSkinSwapperConfig.save()
            }
            .build()
            .generateScreen(parent)
    }
}
