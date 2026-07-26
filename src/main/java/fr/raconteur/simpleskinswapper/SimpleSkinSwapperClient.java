package fr.raconteur.simpleskinswapper;

import com.mojang.blaze3d.platform.InputConstants;
import fr.raconteur.simpleskinswapper.changeskin.StartupSkinSync;
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig;
import fr.raconteur.simpleskinswapper.networking.SkinShuffleCompat;
import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen;
import fr.raconteur.simpleskinswapper.gui.SkinShuffleImporter;
import fr.raconteur.simpleskinswapper.gui.SkinWheelScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SimpleSkinSwapperClient implements ClientModInitializer {

    public static KeyMapping openCarouselKey;
    public static KeyMapping openWheelKey;
    /** Accumulated tick delta for animations (incremented each game tick). */
    public static float TOTAL_TICK_DELTA = 0;

    @Override
    public void onInitializeClient() {
        SkinShuffleCompat.init();
        SkinShuffleImporter.importIfNeeded();
        StartupSkinSync.run();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.parse("simpleskinswapper.title"));
        openCarouselKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.simpleskinswapper.open_carousel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
        ));

        openWheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.simpleskinswapper.open_wheel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
        ));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getCurrentServer() != null) {
                SimpleSkinSwapperConfig.get().registerServerIfAbsent(client.getCurrentServer().ip);
            }
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TOTAL_TICK_DELTA++;
            if (openCarouselKey.consumeClick()) {
                client.gui.setScreen(new SkinCarouselScreen(client.gui.screen()));
            }
            if (openWheelKey.consumeClick()) {
                client.gui.setScreen(new SkinWheelScreen(client.gui.screen()));
            }
        });
    }
}
