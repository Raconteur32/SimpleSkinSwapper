package fr.raconteur.simpleskinswapper.gui.config

import dev.isxander.yacl3.api.Controller
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.controller.ControllerBuilder
import fr.raconteur.simpleskinswapper.config.ServerCommand

class ServerCommandControllerBuilder(private val option: Option<ServerCommand>) :
    ControllerBuilder<ServerCommand> {

    override fun build(): Controller<ServerCommand> = ServerCommandController(option)

    companion object {
        @JvmStatic
        fun create(option: Option<ServerCommand>): ServerCommandControllerBuilder =
            ServerCommandControllerBuilder(option)
    }
}
