package fr.raconteur.simpleskinswapper.mixin.client;

import fr.raconteur.simpleskinswapper.gui.SkinPreviewCache;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the skin-preview bake queue at the head of GUI rendering — the same GPU, projection
 * and UI-lightmap conditions vanilla's picture-in-picture prepare runs under.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    //? if >=26.2 {
    @Inject(method = "render", at = @At("HEAD"))
    private void simpleskinswapper$bakeSkinPreviews(CallbackInfo ci) {
        SkinPreviewCache.processBakeQueue();
    }
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void simpleskinswapper$bakeSkinPreviews(com.mojang.blaze3d.buffers.GpuBufferSlice guiBufferSlice, CallbackInfo ci) {
        SkinPreviewCache.processBakeQueue();
    }
    *///?}
}
