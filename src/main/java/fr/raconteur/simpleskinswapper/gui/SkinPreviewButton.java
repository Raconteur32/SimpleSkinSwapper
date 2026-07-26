package fr.raconteur.simpleskinswapper.gui;

import fr.raconteur.simpleskinswapper.changeskin.SelectedSkinStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public class SkinPreviewButton extends Button {

    private final int previewWidth;
    private final int previewHeight;
    private final int previewGap;

    public SkinPreviewButton(int x, int y, int width, int height,
                              int previewWidth, int previewHeight, int previewGap,
                              Component message, Button.OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.previewGap = previewGap;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractDefaultSprite(graphics);
        this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        this.extractPreview(graphics, mouseX, mouseY);
    }

    private void extractPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Identifier texture = SelectedSkinStore.getPreviewTexture();
        if (texture == null) return;

        SkinType skinType = SelectedSkinStore.getPreviewSkinType();
        PlayerModelType modelType = (skinType == SkinType.SLIM) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        PlayerSkin skin = new PlayerSkin(
                new ClientAsset.DownloadedTexture(texture, ""), null, null,
                modelType, true
        );

        int centerX = this.getX() + this.getWidth() / 2;
        int y2 = this.getY() - previewGap;
        int y1 = y2 - previewHeight;
        int x1 = centerX - previewWidth / 2;
        int x2 = centerX + previewWidth / 2;

        SkinRenderer.renderPlayerFollowingMouse(graphics, x1, y1, x2, y2, previewHeight / 2, skin, mouseX, mouseY);
    }
}
