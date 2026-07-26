package fr.raconteur.simpleskinswapper.mixin.menu;

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
        Button quitBtn = null;
        for (var element : this.children()) {
            if (element instanceof Button btn && btn.getMessage().getContents() instanceof TranslatableContents tc
                    && "menu.quit".equals(tc.getKey())) {
                quitBtn = btn;
                break;
            }
        }
        if (quitBtn == null) return;

        // The accessibility icon normally sits immediately right of Quit; shift it further right
        // to make room for our button in that spot.
        SpriteIconButton accessibilityBtn = null;
        for (var element : this.children()) {
            if (element instanceof SpriteIconButton icon
                    && icon.getY() == quitBtn.getY() && icon.getX() > quitBtn.getX()) {
                accessibilityBtn = icon;
                break;
            }
        }

        TitleScreen self = (TitleScreen) (Object) this;
        int btnX = quitBtn.getX() + quitBtn.getWidth() + 4;
        int btnY = quitBtn.getY();
        int btnW = 72;

        this.addRenderableWidget(new SkinPreviewButton(
                btnX, btnY, btnW, 20,
                PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_GAP,
                Component.translatable("simpleskinswapper.screen.carousel.title"),
                btn -> this.minecraft.setScreen(new SkinCarouselScreen(self))
        ));

        if (accessibilityBtn != null) {
            accessibilityBtn.setPosition(btnX + btnW + 4, accessibilityBtn.getY());
        }
    }
}
