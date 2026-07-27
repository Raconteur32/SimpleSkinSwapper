package fr.raconteur.simpleskinswapper.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceContainerWidget;
import fr.raconteur.simpleskinswapper.changeskin.SkinChange;
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

public class SkinCard extends SpruceContainerWidget {

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 4;
    // Vertical drag limit: enough to see under the chin / over the head without flipping past horizontal.
    private static final float MAX_PITCH = 45.0F;
    private static final float DRAG_SENSITIVITY = 1.0F;
    // Higher = snappier spring-back to the initial orientation once the drag is released.
    private static final float SPRING_RETURN_SPEED = 10.0F;
    private static final float SPRING_SNAP_EPSILON = 0.05F;

    private final SkinEntry entry;
    private final SkinCarouselScreen parent;
    private final SpruceButtonWidget applyButton;
    private final SpruceButtonWidget leftArrow;
    private final SpruceButtonWidget rightArrow;
    private final SpruceButtonWidget typeButton;
    private final SpruceButtonWidget deleteButton;
    private final SpruceButtonWidget confirmDeleteButton;

    private boolean confirmingDelete = false;
    private boolean rotatingPreview = false;
    private float previewYaw = 0.0F;
    private float previewPitch = 0.0F;
    private long lastSpringUpdateNanos = 0L;

    public SkinCard(SkinCarouselScreen parent, SkinEntry entry, int width, int height) {
        super(Position.of(0, 0), width, height);
        this.parent = parent;
        this.entry = entry;

        int halfW = (width - BUTTON_MARGIN * 3) / 2;

        applyButton = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 3),
                width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                Component.translatable("simpleskinswapper.screen.carousel.apply"),
                button -> applySkin()
        );
        addChild(applyButton);

        typeButton = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT * 2 - BUTTON_MARGIN * 2),
                halfW, BUTTON_HEIGHT,
                typeLabel(),
                button -> toggleType()
        );
        addChild(typeButton);

        deleteButton = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN * 2 + halfW, height - BUTTON_HEIGHT * 2 - BUTTON_MARGIN * 2),
                halfW, BUTTON_HEIGHT,
                Component.translatable("simpleskinswapper.screen.carousel.delete"),
                button -> beginDeleteConfirmation()
        );
        addChild(deleteButton);

        leftArrow = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT - BUTTON_MARGIN),
                halfW, BUTTON_HEIGHT,
                Component.literal("←"),
                button -> parent.moveCard(this, -1)
        );
        addChild(leftArrow);

        rightArrow = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN * 2 + halfW, height - BUTTON_HEIGHT - BUTTON_MARGIN),
                halfW, BUTTON_HEIGHT,
                Component.literal("→"),
                button -> parent.moveCard(this, +1)
        );
        addChild(rightArrow);

        int deleteBlockTop = height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 3;
        int deleteBlockHeight = BUTTON_HEIGHT * 3 + BUTTON_MARGIN * 2;
        confirmDeleteButton = new SpruceButtonWidget(
                Position.of(BUTTON_MARGIN, deleteBlockTop + (deleteBlockHeight - BUTTON_HEIGHT) / 2),
                width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                Component.translatable("simpleskinswapper.screen.carousel.delete_confirm"),
                button -> confirmDelete()
        );
        confirmDeleteButton.setVisible(false);
        addChild(confirmDeleteButton);
    }

    public void updateArrowStates(boolean canMoveLeft, boolean canMoveRight) {
        leftArrow.setActive(canMoveLeft);
        rightArrow.setActive(canMoveRight);
    }

    SkinEntry getEntry() {
        return entry;
    }

    private Component typeLabel() {
        return Component.translatable("simpleskinswapper.screen.carousel.skin_type." + entry.skinType.getMojangVariant());
    }

    private void toggleType() {
        entry.skinType = (entry.skinType == SkinType.CLASSIC) ? SkinType.SLIM : SkinType.CLASSIC;
        SkinTypeStore.setType(entry.file.getName(), entry.skinType);
        typeButton.setMessage(typeLabel());
    }

    private void beginDeleteConfirmation() {
        confirmingDelete = true;
        setNormalButtonsVisible(false);
        confirmDeleteButton.setVisible(true);
    }

    private void cancelDeleteConfirmation() {
        confirmingDelete = false;
        setNormalButtonsVisible(true);
        confirmDeleteButton.setVisible(false);
    }

    private void confirmDelete() {
        confirmingDelete = false;
        parent.deleteEntry(entry);
    }

    private void setNormalButtonsVisible(boolean visible) {
        applyButton.setVisible(visible);
        typeButton.setVisible(visible);
        deleteButton.setVisible(visible);
        leftArrow.setVisible(visible);
        rightArrow.setVisible(visible);
    }

    private boolean isMouseOverCard(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    private void applySkin() {
        if (!SkinSwapperState.beginSwap()) return;
        SkinChange.changeSkin(entry.file, entry.skinType, entry.textureId,
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
    protected boolean onMouseClick(MouseButtonEvent event, boolean doubleClick) {
        if (super.onMouseClick(event, doubleClick)) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            rotatingPreview = true;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        if (rotatingPreview && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            previewYaw = Mth.wrapDegrees(previewYaw - (float) deltaX * DRAG_SENSITIVITY);
            previewPitch = Mth.clamp(previewPitch - (float) deltaY * DRAG_SENSITIVITY, -MAX_PITCH, MAX_PITCH);
            return true;
        }
        return super.onMouseDrag(event, deltaX, deltaY);
    }

    @Override
    protected boolean onMouseRelease(MouseButtonEvent event) {
        if (rotatingPreview) {
            rotatingPreview = false;
            return true;
        }
        return super.onMouseRelease(event);
    }

    private void updateSpringBack() {
        long now = System.nanoTime();
        float dt = lastSpringUpdateNanos == 0L ? 0.0F : (now - lastSpringUpdateNanos) / 1_000_000_000.0F;
        lastSpringUpdateNanos = now;

        if (rotatingPreview || (previewYaw == 0.0F && previewPitch == 0.0F)) {
            return;
        }

        float t = 1.0F - (float) Math.exp(-SPRING_RETURN_SPEED * dt);
        previewYaw = Mth.lerp(t, previewYaw, 0.0F);
        previewPitch = Mth.lerp(t, previewPitch, 0.0F);

        if (Math.abs(previewYaw) < SPRING_SNAP_EPSILON) previewYaw = 0.0F;
        if (Math.abs(previewPitch) < SPRING_SNAP_EPSILON) previewPitch = 0.0F;
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
        if (confirmingDelete && !isMouseOverCard(mouseX, mouseY)) {
            cancelDeleteConfirmation();
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        updateSpringBack();

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
            SkinRenderer.renderPlayerRotatable(graphics.vanilla(), previewLeft, previewTop, previewRight, previewBottom, size, skinTextures, previewYaw, previewPitch);
        }
    }
}
