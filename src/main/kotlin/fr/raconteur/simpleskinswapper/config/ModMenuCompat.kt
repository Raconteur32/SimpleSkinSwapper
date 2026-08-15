package fr.raconteur.simpleskinswapper.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import fr.raconteur.simpleskinswapper.gui.config.YaclConfigScreen

class ModMenuCompat : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> YaclConfigScreen.create(parent) }
}
