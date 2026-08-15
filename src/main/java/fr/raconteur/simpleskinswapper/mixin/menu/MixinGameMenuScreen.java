package fr.raconteur.simpleskinswapper.mixin.menu;

import fr.raconteur.simpleskinswapper.config.ButtonSide;
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig;
import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen;
import fr.raconteur.simpleskinswapper.gui.SkinPreviewButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MixinGameMenuScreen extends Screen {

    private static final int PREVIEW_HEIGHT = 92;
    private static final int PREVIEW_WIDTH = PREVIEW_HEIGHT / 2;
    private static final int PREVIEW_GAP = 4;

    protected MixinGameMenuScreen() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void simpleskinswapper$addButton(CallbackInfo ci) {
        Button exitBtn = null;
        for (var element : this.children()) {
            if (element instanceof Button btn && btn.getMessage().getContents() instanceof TranslatableContents tc) {
                String key = tc.getKey();
                if ("menu.disconnect".equals(key) || "menu.returnToMenu".equals(key)) {
                    exitBtn = btn;
                    break;
                }
            }
        }
        if (exitBtn == null) return;

        PauseScreen self = (PauseScreen) (Object) this;
        boolean left = SimpleSkinSwapperConfig.get().pauseMenuSide() == ButtonSide.LEFT;
        int btnW = 72;
        int btnX = left ? exitBtn.getX() - 4 - btnW : exitBtn.getX() + exitBtn.getWidth() + 4;
        int btnY = exitBtn.getY();

        this.addRenderableWidget(new SkinPreviewButton(
                btnX, btnY, btnW, 20,
                PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_GAP,
                Component.translatable("simpleskinswapper.screen.carousel.title"),
                btn -> {
                    //? if >=26.2 {
                    this.minecraft.gui.setScreen(new SkinCarouselScreen(self));
                    //?} else {
                    /*this.minecraft.setScreen(new SkinCarouselScreen(self));
                    *///?}
                }
        ));
    }
}
