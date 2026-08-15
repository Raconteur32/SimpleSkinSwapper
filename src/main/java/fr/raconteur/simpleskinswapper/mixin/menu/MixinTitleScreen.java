package fr.raconteur.simpleskinswapper.mixin.menu;

import fr.raconteur.simpleskinswapper.config.ButtonSide;
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig;
import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen;
import fr.raconteur.simpleskinswapper.gui.SkinPreviewButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {

    // Preview box size, in pixels; kept identical to the pause menu's so the model renders at the
    // same on-screen size on both screens.
    private static final int PREVIEW_HEIGHT = 92;
    private static final int PREVIEW_WIDTH = PREVIEW_HEIGHT / 2;
    // Vanilla's TitleScreen.init() opens an enlarged 36px gap above the Options/Quit row (vs. the
    // normal 24px row spacing elsewhere), i.e. 16px of edge-to-edge whitespace above a 20px-tall
    // button, instead of the usual 4px. Match that same gap so the preview sits flush with the
    // whitespace Mojang already designed above this row.
    private static final int PREVIEW_GAP = 16;

    protected MixinTitleScreen() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void simpleskinswapper$addButton(CallbackInfo ci) {
        // RIGHT anchors on Quit (button goes right of it); LEFT anchors on Options (button
        // goes left of it) — the two placements are strict mirrors on the same vanilla row.
        boolean left = SimpleSkinSwapperConfig.get().titleScreenSide() == ButtonSide.LEFT;
        String anchorKey = left ? "menu.options" : "menu.quit";

        Button anchorBtn = null;
        for (var element : this.children()) {
            if (element instanceof Button btn && btn.getMessage().getContents() instanceof TranslatableContents tc
                    && anchorKey.equals(tc.getKey())) {
                anchorBtn = btn;
                break;
            }
        }
        if (anchorBtn == null) return;

        // The vanilla icon adjacent to the anchor (accessibility right of Quit, language left
        // of Options) normally sits exactly where our button goes; shift it outward past us.
        SpriteIconButton adjacentIcon = null;
        for (var element : this.children()) {
            if (element instanceof SpriteIconButton icon && icon.getY() == anchorBtn.getY()
                    && (left ? icon.getX() < anchorBtn.getX() : icon.getX() > anchorBtn.getX())) {
                adjacentIcon = icon;
                break;
            }
        }

        TitleScreen self = (TitleScreen) (Object) this;
        int btnW = 72;
        int btnX = left ? anchorBtn.getX() - 4 - btnW : anchorBtn.getX() + anchorBtn.getWidth() + 4;
        int btnY = anchorBtn.getY();

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

        if (adjacentIcon != null) {
            adjacentIcon.setPosition(left ? btnX - 4 - adjacentIcon.getWidth() : btnX + btnW + 4, adjacentIcon.getY());
        }
    }
}
