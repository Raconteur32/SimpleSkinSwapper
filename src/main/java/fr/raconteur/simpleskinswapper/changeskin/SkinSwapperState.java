package fr.raconteur.simpleskinswapper.changeskin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class SkinSwapperState {

    public enum State {
        /** No swap in progress. Ready to accept a new swap request. */
        READY_FOR_SWAP,
        /** User has initiated a swap; upload may be ongoing. */
        SWAP_IN_PROGRESS,
        /** The server command is being prepared or is waiting for its delay. */
        COMMAND_IN_PROGRESS,
        /** The command was sent; waiting for the server to push back a PlayerList packet. */
        WAITING_FOR_COMMAND_RESPONSE
    }

    private static volatile State current = State.READY_FOR_SWAP;

    public static State get() {
        return current;
    }

    /**
     * Starts a skin swap. Returns false if a swap is already in progress,
     * sending a chat message to the player.
     */
    public static synchronized boolean beginSwap() {
        if (current != State.READY_FOR_SWAP) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.sendSystemMessage(
                        Component.translatable("simpleskinswapper.message.swap_in_progress"));
            }
            return false;
        }
        current = State.SWAP_IN_PROGRESS;
        return true;
    }

    /**
     * Resets state to READY_FOR_SWAP. Always succeeds.
     */
    public static synchronized void endSwap() {
        current = State.READY_FOR_SWAP;
    }

    /**
     * Transitions to COMMAND_IN_PROGRESS.
     * Returns false (and does nothing) if the current state is not SWAP_IN_PROGRESS.
     */
    public static synchronized boolean beginCommand() {
        if (current != State.SWAP_IN_PROGRESS) return false;
        current = State.COMMAND_IN_PROGRESS;
        return true;
    }

    /**
     * Transitions to WAITING_FOR_COMMAND_RESPONSE.
     * Called just before the command is actually sent to the server.
     */
    public static synchronized void waitForCommandResult() {
        current = State.WAITING_FOR_COMMAND_RESPONSE;
    }

    /**
     * Called in the PlayerList mixin once the local player's entry is confirmed in the packet.
     * Transitions back to SWAP_IN_PROGRESS so the texture comparison can proceed.
     * Returns false if the current state is not WAITING_FOR_COMMAND_RESPONSE,
     * signalling the mixin to ignore this packet.
     */
    public static synchronized boolean commandResultReceived() {
        if (current != State.WAITING_FOR_COMMAND_RESPONSE) return false;
        current = State.SWAP_IN_PROGRESS;
        return true;
    }
}
