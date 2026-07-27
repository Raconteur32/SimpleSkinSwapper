package fr.raconteur.simpleskinswapper.gui;

import fr.raconteur.simpleskinswapper.changeskin.SelectedSkinStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

public class SkinPreviewButton extends ButtonWidget {

    private final int previewWidth;
    private final int previewHeight;
    private final int previewGap;

    public SkinPreviewButton(int x, int y, int width, int height,
                              int previewWidth, int previewHeight, int previewGap,
                              net.minecraft.text.Text message, ButtonWidget.PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.previewGap = previewGap;
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        this.drawButton(context);
        this.drawLabel(context.getHoverListener(this, DrawContext.HoverType.NONE));
        this.drawPreview(context, mouseX, mouseY);
    }

    private void drawPreview(DrawContext context, int mouseX, int mouseY) {
        Identifier texture = SelectedSkinStore.getPreviewTexture();
        if (texture == null) return;

        SkinType skinType = SelectedSkinStore.getPreviewSkinType();
        PlayerSkinType modelType = (skinType == SkinType.SLIM) ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
        SkinTextures skin = new SkinTextures(
                new AssetInfo.SkinAssetInfo(texture, ""), null, null,
                modelType, true
        );

        int centerX = this.getX() + this.getWidth() / 2;
        int y2 = this.getY() - previewGap;
        int y1 = y2 - previewHeight;
        int x1 = centerX - previewWidth / 2;
        int x2 = centerX + previewWidth / 2;

        SkinRenderer.renderPlayerFollowingMouse(context, x1, y1, x2, y2, previewHeight / 2, skin, mouseX, mouseY);
    }
}
