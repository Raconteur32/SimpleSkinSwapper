package fr.raconteur.simpleskinswapper.mixin.player;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractClientPlayer.class)
public interface AbstractClientPlayerAccessor {
    @Accessor("playerInfo")
    void simpleSkinSwapper$setPlayerInfo(PlayerInfo info);
}
