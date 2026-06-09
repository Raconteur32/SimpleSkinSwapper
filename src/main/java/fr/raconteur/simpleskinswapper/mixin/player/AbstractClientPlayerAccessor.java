package fr.raconteur.simpleskinswapper.mixin.player;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractClientPlayerEntity.class)
public interface AbstractClientPlayerAccessor {
    @Accessor("playerListEntry")
    void simpleSkinSwapper$setPlayerInfo(PlayerListEntry entry);
}
