package fr.raconteur.simpleskinswapper.gui.config

import dev.isxander.yacl3.api.Controller
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import dev.isxander.yacl3.gui.YACLScreen
import fr.raconteur.simpleskinswapper.config.ServerCommand
import net.minecraft.network.chat.Component

/**
 * Controller for a (server address, command) pair, edited as two text fields
 * with a clear button (see [ServerCommandControllerElement]).
 */
class ServerCommandController(private val option: Option<ServerCommand>) : Controller<ServerCommand> {

    override fun option(): Option<ServerCommand> = option

    override fun formatValue(): Component {
        val value = option.pendingValue()
        return Component.literal("${value.address} → ${value.command}")
    }

    override fun provideWidget(screen: YACLScreen, widgetDimension: Dimension<Int>): AbstractWidget =
        ServerCommandControllerElement(this, widgetDimension)
}
