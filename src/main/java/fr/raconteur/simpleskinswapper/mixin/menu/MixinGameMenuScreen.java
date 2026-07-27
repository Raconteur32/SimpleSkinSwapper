package fr.raconteur.simpleskinswapper.mixin.menu;

import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen;
import fr.raconteur.simpleskinswapper.gui.SkinPreviewButton;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class MixinGameMenuScreen extends Screen {

    private static final int PREVIEW_HEIGHT = 92;
    private static final int PREVIEW_WIDTH = PREVIEW_HEIGHT / 2;
    private static final int PREVIEW_GAP = 4;

    protected MixinGameMenuScreen() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void simpleskinswapper$addButton(CallbackInfo ci) {
        ButtonWidget exitBtn = null;
        for (var element : this.children()) {
            if (element instanceof ButtonWidget btn) {
                if (btn.getMessage().getContent() instanceof TranslatableTextContent tc) {
                    String key = tc.getKey();
                    if ("menu.disconnect".equals(key) || "menu.returnToMenu".equals(key)) {
                        exitBtn = btn;
                        break;
                    }
                }
            }
        }
        if (exitBtn == null) return;

        GameMenuScreen self = (GameMenuScreen) (Object) this;
        int btnX = exitBtn.getX() + exitBtn.getWidth() + 4;
        int btnY = exitBtn.getY();
        int btnW = 72;

        this.addDrawableChild(new SkinPreviewButton(
                btnX, btnY, btnW, 20,
                PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_GAP,
                Text.translatable("simpleskinswapper.screen.carousel.title"),
                btn -> this.client.setScreen(new SkinCarouselScreen(self))
        ));
    }
}
