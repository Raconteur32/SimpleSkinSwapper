package fr.raconteur.simpleskinswapper.changeskin

import com.mojang.authlib.properties.Property

interface IPlayerSkinUpdatable {
    fun `simpleSkinSwapper$setGameProfileWithTexture`(textureProperty: Property)
}
