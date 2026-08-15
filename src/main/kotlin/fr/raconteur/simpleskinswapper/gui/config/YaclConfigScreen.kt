package fr.raconteur.simpleskinswapper.gui.config

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import fr.raconteur.simpleskinswapper.config.ButtonSide
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
            .category(
                ConfigCategory.createBuilder()
                    .name(Component.translatable("simpleskinswapper.config.category.menu_buttons"))
                    .option(
                        buttonSideOption(
                            "simpleskinswapper.config.title_screen_button_side",
                            { config.titleScreenSide() },
                            { config.titleScreenButtonSide = it }
                        )
                    )
                    .option(
                        buttonSideOption(
                            "simpleskinswapper.config.pause_menu_button_side",
                            { config.pauseMenuSide() },
                            { config.pauseMenuButtonSide = it }
                        )
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

    /**
     * Builds a LEFT/RIGHT cycling option for one of the menu-button side settings.
     * [keyPrefix] is the translation key of the option; its description lives at
     * `"$keyPrefix.description"`. Writes go straight to the config object and are
     * persisted by the builder's save callback.
     */
    private fun buttonSideOption(
        keyPrefix: String,
        getter: () -> ButtonSide,
        setter: (ButtonSide) -> Unit
    ): Option<ButtonSide> {
        return Option.createBuilder<ButtonSide>()
            .name(Component.translatable(keyPrefix))
            .description(OptionDescription.of(Component.translatable("$keyPrefix.description")))
            .binding(ButtonSide.RIGHT, getter, setter)
            .controller { option ->
                EnumControllerBuilder.create(option)
                    .enumClass(ButtonSide::class.java)
                    .formatValue { side ->
                        Component.translatable("simpleskinswapper.config.button_side.${side.name.lowercase()}")
                    }
            }
            .build()
    }
}
