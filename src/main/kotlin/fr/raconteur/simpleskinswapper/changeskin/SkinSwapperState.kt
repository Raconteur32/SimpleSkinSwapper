package fr.raconteur.simpleskinswapper.changeskin

import fr.raconteur.simpleskinswapper.systemMessage
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object SkinSwapperState {

    enum class State {
        /** No swap in progress. Ready to accept a new swap request. */
        READY_FOR_SWAP,

        /** User has initiated a swap; upload may be ongoing. */
        SWAP_IN_PROGRESS,

        /** The server command is being prepared or is waiting for its delay. */
        COMMAND_IN_PROGRESS,

        /** The command was sent; waiting for the server to push back a PlayerList packet. */
        WAITING_FOR_COMMAND_RESPONSE
    }

    @Volatile
    private var current = State.READY_FOR_SWAP

    @JvmStatic
    fun get(): State = current

    /**
     * Starts a skin swap. Returns false if a swap is already in progress,
     * sending a chat message to the player.
     */
    @JvmStatic
    @Synchronized
    fun beginSwap(): Boolean {
        if (current != State.READY_FOR_SWAP) {
            val client = Minecraft.getInstance()
            client.player?.systemMessage(
                Component.translatable("simpleskinswapper.message.swap_in_progress")
            )
            return false
        }
        current = State.SWAP_IN_PROGRESS
        return true
    }

    /**
     * Resets state to READY_FOR_SWAP. Always succeeds.
     */
    @JvmStatic
    @Synchronized
    fun endSwap() {
        current = State.READY_FOR_SWAP
    }

    /**
     * Transitions to COMMAND_IN_PROGRESS.
     * Returns false (and does nothing) if the current state is not SWAP_IN_PROGRESS.
     */
    @JvmStatic
    @Synchronized
    fun beginCommand(): Boolean {
        if (current != State.SWAP_IN_PROGRESS) return false
        current = State.COMMAND_IN_PROGRESS
        return true
    }

    /**
     * Transitions to WAITING_FOR_COMMAND_RESPONSE.
     * Called just before the command is actually sent to the server.
     */
    @JvmStatic
    @Synchronized
    fun waitForCommandResult() {
        current = State.WAITING_FOR_COMMAND_RESPONSE
    }

    /**
     * Called in the PlayerList mixin once the local player's entry is confirmed in the packet.
     * Transitions back to SWAP_IN_PROGRESS so the texture comparison can proceed.
     * Returns false if the current state is not WAITING_FOR_COMMAND_RESPONSE,
     * signalling the mixin to ignore this packet.
     */
    @JvmStatic
    @Synchronized
    fun commandResultReceived(): Boolean {
        if (current != State.WAITING_FOR_COMMAND_RESPONSE) return false
        current = State.SWAP_IN_PROGRESS
        return true
    }
}
