package fr.raconteur.simpleskinswapper.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceContainerWidget;
import fr.raconteur.simpleskinswapper.changeskin.SkinChange;
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public class SkinCard extends SpruceContainerWidget {

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 4;

    private final SkinEntry entry;
    private final SkinCarouselScreen parent;
    private final SpruceButtonWidget leftArrow;
    private final SpruceButtonWidget rightArrow;
    private final SpruceButtonWidget typeButton;

    public SkinCard(SkinCarouselScreen parent, SkinEntry entry, int width, int height) {
        super(Position.of(0, 0), width, height);
        this.parent = parent;
        this.entry = entry;

        int arrowW = (width - BUTTON_MARGIN * 3) / 2;

        SpruceButtonWidget applyButton = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 3),
                width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                Component.translatable("simpleskinswapper.screen.carousel.apply"),
                button -> applySkin()
        );
        addChild(applyButton);

        typeButton = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT * 2 - BUTTON_MARGIN * 2),
                width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                typeLabel(),
                button -> toggleType()
        );
        addChild(typeButton);

        leftArrow = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT - BUTTON_MARGIN),
                arrowW, BUTTON_HEIGHT,
                Component.literal("←"),
                button -> parent.moveCard(this, -1)
        );
        addChild(leftArrow);

        rightArrow = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN * 2 + arrowW, height - BUTTON_HEIGHT - BUTTON_MARGIN),
                arrowW, BUTTON_HEIGHT,
                Component.literal("→"),
                button -> parent.moveCard(this, +1)
        );
        addChild(rightArrow);
    }

    public void updateArrowStates(int index, int total) {
        leftArrow.setActive(index > 0);
        rightArrow.setActive(index < total - 1);
    }

    SkinEntry getEntry() {
        return entry;
    }

    private Component typeLabel() {
        return Component.translatable("simpleskinswapper.screen.carousel.type",
                Component.translatable("simpleskinswapper.screen.carousel.skin_type." + entry.skinType.getMojangVariant()));
    }

    private void toggleType() {
        entry.skinType = (entry.skinType == SkinType.CLASSIC) ? SkinType.SLIM : SkinType.CLASSIC;
        SkinTypeStore.setType(entry.file.getName(), entry.skinType);
        typeButton.setMessage(typeLabel());
    }

    private void applySkin() {
        if (!SkinSwapperState.beginSwap()) return;
        SkinChange.changeSkin(entry.file, entry.skinType,
                () -> showOverlay(Component.translatable("simpleskinswapper.message.success")),
                err -> showOverlay(Component.translatable("simpleskinswapper.message.error", err))
        );
        parent.onClose();
        showOverlay(Component.translatable("simpleskinswapper.message.applying"));
    }

    private void showOverlay(Component text) {
        if (client.player != null) {
            client.player.sendOverlayMessage(text);
        }
    }

    public void overridePosition(int x, int y) {
        this.getPosition().move(x, y);
    }

    @Override
    protected void extractBackground(SpruceGuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int borderColor = this.active ? 0xDF000000 : 0x5F000000;
        drawBorder(graphics.vanilla(), getX(), getY(), getWidth(), getHeight(), borderColor);
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
                this.active ? 0x7F000000 : 0x0D000000);
    }

    private void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    @Override
    public void extractRenderState(SpruceGuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int margin = client.font.lineHeight / 2;
        int nameColor = this.active ? 0xFFFFFFFF : 0xFF808080;
        int textWidth = client.font.width(entry.displayName);
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = getY() + margin;
        graphics.vanilla().enableScissor(getX() + margin, textY, getX() + getWidth() - margin, textY + client.font.lineHeight);
        graphics.vanilla().text(client.font, Component.nullToEmpty(entry.displayName), textX, textY, nameColor);
        graphics.vanilla().disableScissor();

        entry.ensureTextureLoaded();

        int previewTop = getY() + margin + client.font.lineHeight + 2;
        int previewBottom = getY() + getHeight() - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 4;
        int previewLeft = getX() + 1;
        int previewRight = getX() + getWidth() - 1;

        if (entry.textureId != null) {
            int size = (int) ((previewBottom - previewTop) * 0.5f);
            PlayerSkin skinTextures = new PlayerSkin(
                    new ClientAsset.DownloadedTexture(entry.textureId, ""), null, null,
                    entry.skinType == SkinType.SLIM ? PlayerModelType.SLIM : PlayerModelType.WIDE,
                    true
            );
            SkinRenderer.renderPlayer(graphics.vanilla(), previewLeft, previewTop, previewRight, previewBottom, size, skinTextures);
        }
    }
}
