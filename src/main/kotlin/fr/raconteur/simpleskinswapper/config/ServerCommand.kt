package fr.raconteur.simpleskinswapper.config

/**
 * One row of the config screen's server list: a server address and the
 * command sent to it when applying a skin.
 */
data class ServerCommand(
    var address: String,
    var command: String,
)
