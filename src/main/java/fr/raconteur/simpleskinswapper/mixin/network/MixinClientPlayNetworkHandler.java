package fr.raconteur.simpleskinswapper.mixin.network;

import com.mojang.authlib.properties.Property;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import fr.raconteur.simpleskinswapper.changeskin.SkinChangeManager;
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState;
import fr.raconteur.simpleskinswapper.mixin.player.AbstractClientPlayerAccessor;
import fr.raconteur.simpleskinswapper.networking.SkinShuffleCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPlayNetworkHandler {

    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    private void simpleskinswapper$afterPlayerList(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return;

        boolean localPlayerInPacket = false;
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            if (entry.profileId().equals(localPlayer.getUUID())) {
                localPlayerInPacket = true;
                break;
            }
        }
        if (!localPlayerInPacket) return;

        // Direct packet path (singleplayer / Fabric server with the mod): reset cached PlayerInfo
        // so the player entity picks up the new GameProfile with the updated skin texture.
        if (SkinShuffleCompat.consumeAwaitingSkinRefresh()) {
            ((AbstractClientPlayerAccessor) localPlayer).simpleSkinSwapper$setPlayerInfo(null);
            SimpleSkinSwapper.LOGGER.info("[SkinSwap] PlayerInfo cache cleared — skin will reload.");
            return;
        }

        // Server command path (external plugin / vanilla server)
        String pendingTextureValue = SkinChangeManager.pendingCommandTextureValue;
        if (pendingTextureValue == null) return;

        // Validate state: only proceed if we are WAITING_FOR_COMMAND_RESPONSE
        if (!SkinSwapperState.commandResultReceived()) return;

        // Signal the timeout that a response arrived — cancels any pending timeout for this send
        SkinChangeManager.commandResponseSignal.set(true);

        // Read the texture value now stored in the (already-updated) PlayerListEntry
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        if (networkHandler == null) return;

        String currentTextureValue = null;
        for (PlayerInfo listEntry : networkHandler.getOnlinePlayers()) {
            if (listEntry.getProfile().id().equals(localPlayer.getUUID())) {
                Property textures = listEntry.getProfile().properties()
                        .get("textures").stream().findFirst().orElse(null);
                currentTextureValue = textures != null ? textures.value() : null;
                break;
            }
        }

        if (pendingTextureValue.equals(currentTextureValue)) {
            // Texture unchanged — server hasn't applied the skin yet, retry
            SimpleSkinSwapper.LOGGER.info("[SkinSwap] Texture unchanged after server command, retrying (attempt {}).",
                    SkinChangeManager.commandAttempt + 1);
            SkinChangeManager.sendServerCommandIfNeeded(SkinChangeManager.commandAttempt + 1);
        } else {
            // Texture changed — skin successfully applied
            SimpleSkinSwapper.LOGGER.info("[SkinSwap] Skin texture updated by server.");
            SkinChangeManager.pendingCommandTextureValue = null;
            SkinSwapperState.endSwap();
            //? if >=26.1 {
            localPlayer.sendSystemMessage(
                    Component.translatable("simpleskinswapper.message.command_success"));
            //?} else {
            /*localPlayer.displayClientMessage(
                    Component.translatable("simpleskinswapper.message.command_success"), false);
            *///?}
        }
    }
}
