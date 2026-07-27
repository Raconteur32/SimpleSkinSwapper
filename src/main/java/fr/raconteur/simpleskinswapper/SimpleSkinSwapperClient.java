package fr.raconteur.simpleskinswapper;

import fr.raconteur.simpleskinswapper.changeskin.StartupSkinSync;
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig;
import fr.raconteur.simpleskinswapper.networking.SkinShuffleCompat;
import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen;
import fr.raconteur.simpleskinswapper.gui.SkinShuffleImporter;
import fr.raconteur.simpleskinswapper.gui.SkinWheelScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SimpleSkinSwapperClient implements ClientModInitializer {

    public static KeyBinding openCarouselKey;
    public static KeyBinding openWheelKey;
    /** Accumulated tick delta for animations (incremented each game tick). */
    public static float TOTAL_TICK_DELTA = 0;

    @Override
    public void onInitializeClient() {
        SkinShuffleCompat.init();
        SkinShuffleImporter.importIfNeeded();
        StartupSkinSync.run();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("simpleskinswapper.title"));
        openCarouselKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.simpleskinswapper.open_carousel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
        ));

        openWheelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.simpleskinswapper.open_wheel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
        ));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getCurrentServerEntry() != null) {
                SimpleSkinSwapperConfig.get().registerServerIfAbsent(client.getCurrentServerEntry().address);
            }
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TOTAL_TICK_DELTA++;
            if (openCarouselKey.wasPressed()) {
                client.setScreen(new SkinCarouselScreen(client.currentScreen));
            }
            if (openWheelKey.wasPressed()) {
                client.setScreen(new SkinWheelScreen(client.currentScreen));
            }
        });
    }
}
