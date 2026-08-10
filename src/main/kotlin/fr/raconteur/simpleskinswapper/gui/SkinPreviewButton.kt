package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.changeskin.SelectedSkinStore
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin

class SkinPreviewButton(
    x: Int, y: Int, width: Int, height: Int,
    private val previewWidth: Int,
    private val previewHeight: Int,
    private val previewGap: Int,
    message: Component,
    onPress: OnPress
) : Button(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION) {

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        this.extractDefaultSprite(graphics)
        this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE))
        this.renderPreview(graphics, mouseX, mouseY)
    }

    private fun renderPreview(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val texture = SelectedSkinStore.getPreviewTexture() ?: return

        val skinType = SelectedSkinStore.getPreviewSkinType()
        val modelType = if (skinType == SkinType.SLIM) PlayerModelType.SLIM else PlayerModelType.WIDE
        val skin = PlayerSkin(
            ClientAsset.DownloadedTexture(texture, ""), null, null,
            modelType, true
        )

        val centerX = this.x + this.width / 2
        val y2 = this.y - previewGap
        val y1 = y2 - previewHeight
        val x1 = centerX - previewWidth / 2
        val x2 = centerX + previewWidth / 2

        SkinRenderer.renderPlayerFollowingMouse(graphics, x1, y1, x2, y2, previewHeight / 2, skin, mouseX, mouseY)
    }
}
