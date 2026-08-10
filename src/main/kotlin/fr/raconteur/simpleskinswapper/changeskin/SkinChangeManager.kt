package fr.raconteur.simpleskinswapper.changeskin

import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SkinChangeManager {

    /**
     * The "textures" property value recorded when the server command was sent.
     * Used to detect whether the server has actually applied the new skin.
     * Null when no command is pending.
     */
    @JvmField
    @Volatile
    var pendingCommandTextureValue: String? = null

    /**
     * The current attempt index (0 = first send, 1 = first retry, 2 = second retry).
     * Read by the mixin to compute the next attempt number.
     */
    @JvmField
    @Volatile
    var commandAttempt = 0

    /**
     * Signal set to true by the mixin as soon as a response packet is received,
     * regardless of whether the skin changed or not.
     * Each command send replaces this with a fresh AtomicBoolean(false).
     * The timeout closure captures the reference at send time, so an old timeout
     * can never fire on a newer attempt's WAITING state.
     */
    @JvmField
    @Volatile
    var commandResponseSignal = AtomicBoolean(false)

    /** Maximum number of attempts (0, 1, 2). Beyond this, we give up. */
    private const val MAX_ATTEMPTS = 3

    /** Delays in seconds before attempt index 1 and 2. */
    private val RETRY_DELAYS_SECONDS = longArrayOf(5, 20)

    @JvmStatic
    fun sendServerCommandIfNeeded() {
        sendServerCommandIfNeeded(0)
    }

    /**
     * Sends the configured server command, or notifies the player to reconnect
     * if no command is configured.
     *
     * Attempt 0 fires immediately. Attempt 1 fires after 5 s. Attempt 2 fires
     * after 20 s. Attempt 3 (and beyond) abandons the swap entirely.
     *
     * Requires state SWAP_IN_PROGRESS (via beginCommand) for attempts 0–2.
     * For the abandon path the state is transitioned directly to READY_FOR_SWAP.
     *
     * @param attempt 0-based attempt index
     */
    @JvmStatic
    fun sendServerCommandIfNeeded(attempt: Int) {
        val client = Minecraft.getInstance()

        if (attempt >= MAX_ATTEMPTS) {
            // All retries exhausted — give up and tell the player
            client.execute {
                client.player?.sendSystemMessage(
                    Component.translatable("simpleskinswapper.message.command_give_up")
                )
            }
            SkinSwapperState.endSwap()
            return
        }

        if (!SkinSwapperState.beginCommand()) return
        commandAttempt = attempt

        val config = SimpleSkinSwapperConfig.get()

        val serverInfo = client.currentServer
        if (serverInfo == null) {
            // Singleplayer — no server command needed
            SkinSwapperState.endSwap()
            return
        }

        val serverAddress = serverInfo.ip
        val serverCmd = config.getCommandForServer(serverAddress)

        if (serverCmd.isNullOrBlank()) {
            client.execute {
                client.player?.sendSystemMessage(
                    Component.translatable("simpleskinswapper.message.command_not_defined", serverAddress)
                )
            }
            SkinSwapperState.endSwap()
            return
        }

        if (client.connection == null) {
            SkinSwapperState.endSwap()
            return
        }

        val cmd = serverCmd.trim()

        client.execute {
            // Record the current texture value before sending the command
            if (client.player != null) {
                pendingCommandTextureValue = null
                for (listEntry in client.connection!!.onlinePlayers) {
                    if (listEntry.profile.id() == client.player!!.uuid) {
                        val textures = listEntry.profile.properties()
                            .get("textures").stream().findFirst().orElse(null)
                        pendingCommandTextureValue = textures?.value()
                        break
                    }
                }
            }

            // Notify the player
            if (client.player != null) {
                val message: Component = if (attempt == 0) {
                    Component.translatable("simpleskinswapper.message.command_pending")
                } else {
                    val delaySeconds = RETRY_DELAYS_SECONDS[attempt - 1]
                    Component.translatable(
                        "simpleskinswapper.message.command_retry",
                        delaySeconds, attempt + 1, MAX_ATTEMPTS
                    )
                }
                client.player!!.sendSystemMessage(message)
            }

            val sendCmd = Runnable {
                client.execute {
                    if (client.connection == null) {
                        SkinSwapperState.endSwap()
                        return@execute
                    }

                    // Fresh signal for this specific send — captured in the timeout closure
                    val signal = AtomicBoolean(false)
                    commandResponseSignal = signal

                    SkinSwapperState.waitForCommandResult()
                    if (cmd.startsWith("/")) {
                        client.connection!!.sendCommand(cmd.substring(1))
                    } else {
                        client.connection!!.sendChat(cmd)
                    }

                    // Timeout: if no response is received within 5 s for THIS send, give up
                    CompletableFuture.runAsync({
                        client.execute {
                            if (!signal.get()) {
                                pendingCommandTextureValue = null
                                SkinSwapperState.endSwap()
                                client.player?.sendSystemMessage(
                                    Component.translatable("simpleskinswapper.message.command_timeout")
                                )
                            }
                        }
                    }, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS))
                }
            }

            if (attempt == 0) {
                sendCmd.run()
            } else {
                val delaySeconds = RETRY_DELAYS_SECONDS[attempt - 1]
                CompletableFuture.runAsync(
                    sendCmd,
                    CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS)
                )
            }
        }
    }
}
