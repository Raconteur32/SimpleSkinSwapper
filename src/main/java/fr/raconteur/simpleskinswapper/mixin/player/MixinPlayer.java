package fr.raconteur.simpleskinswapper.mixin.player;

import com.google.common.collect.ImmutableListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import fr.raconteur.simpleskinswapper.changeskin.IPlayerSkinUpdatable;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class MixinPlayer implements IPlayerSkinUpdatable {

    @Mutable
    @Shadow
    private GameProfile gameProfile;

    @Override
    @Unique
    public void simpleSkinSwapper$setGameProfileWithTexture(Property textureProperty) {
        // PropertyMap always wraps in ImmutableMultimap.copyOf(), so build the final
        // immutable multimap directly instead of trying to mutate after construction.
        ImmutableListMultimap.Builder<String, Property> builder = ImmutableListMultimap.builder();
        for (var entry : this.gameProfile.properties().entries()) {
            if (!entry.getKey().equals("textures")) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        builder.put("textures", textureProperty);
        this.gameProfile = new GameProfile(
                this.gameProfile.id(),
                this.gameProfile.name(),
                new PropertyMap(builder.build()));
    }
}
