package fr.raconteur.simpleskinswapper.mixin.menu;

import fr.raconteur.simpleskinswapper.gui.SkinCarouselScreen;
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

    protected MixinGameMenuScreen() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void simpleskinswapper$addButton(CallbackInfo ci) {
        Button exitBtn = null;
        for (var element : this.children()) {
            if (element instanceof Button btn) {
                if (btn.getMessage().getContents() instanceof TranslatableContents tc) {
                    String key = tc.getKey();
                    if ("menu.disconnect".equals(key) || "menu.returnToMenu".equals(key)) {
                        exitBtn = btn;
                        break;
                    }
                }
            }
        }
        if (exitBtn == null) return;

        PauseScreen self = (PauseScreen) (Object) this;
        this.addRenderableWidget(Button.builder(
                Component.translatable("simpleskinswapper.screen.carousel.title"),
                btn -> this.minecraft.setScreen(new SkinCarouselScreen(self)))
                .bounds(exitBtn.getX() + exitBtn.getWidth() + 4, exitBtn.getY(), 72, 20)
                .build());
    }
}
