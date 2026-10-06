package fr.raconteur.simpleskinswapper.gui.config

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import fr.raconteur.simpleskinswapper.config.AllSkinsWheelMode
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
            .category(buildOptionsCategory(config))
            .category(buildServersCategory({ staged }, { staged = it.toMutableList() }))
            .save {
                config.applyServerCommandList(staged)
                SimpleSkinSwapperConfig.save()
            }
            .build()
            .generateScreen(parent)
    }

    /** Menu buttons, player models and skin wheel toggles. */
    private fun buildOptionsCategory(config: SimpleSkinSwapperConfig): ConfigCategory =
        ConfigCategory.createBuilder()
            .name(Component.translatable("simpleskinswapper.config.category.options"))
            .group(buildMenuButtonsGroup(config))
            .group(buildPlayerModelsGroup(config))
            .group(buildSkinWheelGroup(config))
            .group(buildLibraryGroup(config))
            .build()

    private fun buildMenuButtonsGroup(config: SimpleSkinSwapperConfig): OptionGroup =
        OptionGroup.createBuilder()
            .name(Component.translatable("simpleskinswapper.config.group.menu_buttons"))
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

    private fun buildPlayerModelsGroup(config: SimpleSkinSwapperConfig): OptionGroup =
        OptionGroup.createBuilder()
            .name(Component.translatable("simpleskinswapper.config.group.player_models"))
            .option(
                tickBoxOption(
                    "simpleskinswapper.config.animate_menu_preview",
                    { config.animateMenuPreview },
                    { config.animateMenuPreview = it },
                    default = true
                )
            )
            .build()

    private fun buildSkinWheelGroup(config: SimpleSkinSwapperConfig): OptionGroup =
        OptionGroup.createBuilder()
            .name(Component.translatable("simpleskinswapper.config.group.skin_wheel"))
            .option(
                enumOption(
                    "simpleskinswapper.config.all_skins_wheel",
                    AllSkinsWheelMode::class.java,
                    AllSkinsWheelMode.FALLBACK,
                    { config.allSkinsWheel() },
                    { config.allSkinsWheelMode = it }
                ) { mode ->
                    Component.translatable("simpleskinswapper.config.all_skins_wheel.${mode.name.lowercase()}")
                }
            )
            .option(
                intSliderOption(
                    "simpleskinswapper.config.max_all_skins_wheels",
                    { config.maxAllSkinsWheels },
                    { config.maxAllSkinsWheels = it },
                    default = 2,
                    min = SimpleSkinSwapperConfig.MIN_ALL_SKINS_WHEELS,
                    max = SimpleSkinSwapperConfig.MAX_ALL_SKINS_WHEELS,
                    step = 1
                )
            )
            .option(
                tickBoxOption(
                    "simpleskinswapper.config.remember_wheel_position",
                    { config.rememberWheelPosition },
                    { config.rememberWheelPosition = it },
                    default = false
                )
            )
            .build()

    private fun buildLibraryGroup(config: SimpleSkinSwapperConfig): OptionGroup =
        OptionGroup.createBuilder()
            .name(Component.translatable("simpleskinswapper.config.group.library"))
            .option(
                intSliderOption(
                    "simpleskinswapper.config.min_card_width",
                    { config.minCardWidth },
                    { config.minCardWidth = it },
                    default = 64,
                    min = SimpleSkinSwapperConfig.MIN_CARD_WIDTH,
                    max = SimpleSkinSwapperConfig.MAX_CARD_WIDTH,
                    step = 8
                )
            )
            .build()

    /** Per-server skin commands list, staged until save. */
    private fun buildServersCategory(
        getStaged: () -> List<ServerCommand>,
        setStaged: (List<ServerCommand>) -> Unit
    ): ConfigCategory =
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
                        { getStaged() },
                        { newList -> setStaged(newList) }
                    )
                    .controller { option -> ServerCommandControllerBuilder.create(option) }
                    .initial(ServerCommand("", ""))
                    .build()
            )
            .build()

    private fun tickBoxOption(
        key: String,
        getter: () -> Boolean,
        setter: (Boolean) -> Unit,
        default: Boolean
    ): Option<Boolean> =
        Option.createBuilder<Boolean>()
            .name(Component.translatable(key))
            .description(OptionDescription.of(Component.translatable("$key.description")))
            .binding(default, getter, setter)
            .controller { option -> TickBoxControllerBuilder.create(option) }
            .build()

    /** Integer slider option; bounds come from the caller (shared with config clamping). */
    private fun intSliderOption(
        key: String,
        getter: () -> Int,
        setter: (Int) -> Unit,
        default: Int,
        min: Int,
        max: Int,
        step: Int
    ): Option<Int> =
        Option.createBuilder<Int>()
            .name(Component.translatable(key))
            .description(OptionDescription.of(Component.translatable("$key.description")))
            .binding(default, getter, setter)
            .controller { option ->
                IntegerSliderControllerBuilder.create(option)
                    .range(min, max)
                    .step(step)
            }
            .build()

    /**
     * Builds a cycling enum option. [keyPrefix] is the translation key of the
     * option; its description lives at `"$keyPrefix.description"`. Writes go
     * straight to the config object and are persisted by the builder's save
     * callback.
     */
    private fun <E : Enum<E>> enumOption(
        keyPrefix: String,
        enumClass: Class<E>,
        default: E,
        getter: () -> E,
        setter: (E) -> Unit,
        formatValue: (E) -> Component
    ): Option<E> {
        return Option.createBuilder<E>()
            .name(Component.translatable(keyPrefix))
            .description(OptionDescription.of(Component.translatable("$keyPrefix.description")))
            .binding(default, getter, setter)
            .controller { option ->
                EnumControllerBuilder.create(option)
                    .enumClass(enumClass)
                    .formatValue { formatValue(it) }
            }
            .build()
    }

    private fun buttonSideOption(
        keyPrefix: String,
        getter: () -> ButtonSide,
        setter: (ButtonSide) -> Unit
    ): Option<ButtonSide> = enumOption(
        keyPrefix,
        ButtonSide::class.java,
        ButtonSide.RIGHT,
        getter,
        setter
    ) { side ->
        Component.translatable("simpleskinswapper.config.button_side.${side.name.lowercase()}")
    }
}
