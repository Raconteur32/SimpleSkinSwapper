package fr.raconteur.simpleskinswapper.gui;

import fr.raconteur.simpleskinswapper.SimpleSkinSwapperClient;
import fr.raconteur.simpleskinswapper.changeskin.SkinChange;
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public class SkinWheelScreen extends Screen {

    private final Screen parent;
    private final List<SkinEntry> entries;
    private int selectedIndex = -1;

    private static final int MAX_ENTRIES = 10;
    private static final float OUTER_RADIUS = 90.0f;
    private static final float GAP_HALF_ANGLE = (float) Math.toRadians(2.0);

    private static final int COLOR_SECTOR       = 0xCC1A2535;
    private static final int COLOR_SECTOR_HOVER = 0xEE2B5F9E;
    private static final int COLOR_CENTER_BG    = 0xBB0D1627;
    private static final int COLOR_TEXT         = 0xFFFFFFFF;

    public SkinWheelScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
        this.entries = loadWheelEntries();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //@Override
    //protected void renderBlurredBackground(GuiGraphicsExtractor context) {
    //    // No blur — wheel is a transparent overlay
    //}

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // No background — wheel is a transparent overlay
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float cx = this.width / 2.0f;
        float cy = this.height / 2.0f;

        selectedIndex = getSelectedIndex(mouseX, mouseY, cx, cy);

        int n = entries.size();
        if (n == 0) {
            context.centeredText(font,
                    Component.translatable("simpleskinswapper.screen.carousel.no_skins"),
                    (int) cx, (int) cy, COLOR_TEXT);
            super.extractRenderState(context, mouseX, mouseY, delta);
            return;
        }

        // Draw pie sector backgrounds
        for (int i = 0; i < n; i++) {
            drawSector(context, cx, cy, i, n, i == selectedIndex);
        }

        // Center fill circle (on top of sectors)
        fillCircle(context, cx, cy, 28, COLOR_CENTER_BG);

        // Skin previews — painter's order: top (smallest py) first
        double sectorSize2 = 2 * Math.PI / n;
        double angleOffset2 = -Math.PI / 2 - sectorSize2 / 2.0;
        double previewDist2 = OUTER_RADIUS * 0.60;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            double pyA = cy + previewDist2 * Math.sin(angleOffset2 + sectorSize2 * a + sectorSize2 / 2.0);
            double pyB = cy + previewDist2 * Math.sin(angleOffset2 + sectorSize2 * b + sectorSize2 / 2.0);
            return Double.compare(pyA, pyB);
        });
        for (int i : order) {
            drawSectorPreview(context, cx, cy, i, n);
        }

        // Selected skin name above the wheel
        if (selectedIndex >= 0) {
            context.centeredText(font,
                    Component.nullToEmpty(entries.get(selectedIndex).displayName),
                    (int) cx, (int) (cy - OUTER_RADIUS) - font.lineHeight - 6, COLOR_TEXT);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    // -------------------------------------------------------------------------
    // Sector geometry
    // -------------------------------------------------------------------------

    private int getSelectedIndex(int mouseX, int mouseY, float cx, float cy) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 10 || dist > OUTER_RADIUS * 1.1) return -1;

        int n = entries.size();
        double sectorSize = 2 * Math.PI / n;
        double angleOffset = -Math.PI / 2 - sectorSize / 2.0;

        double angle = Math.atan2(dy, dx);
        double adjusted = ((angle - angleOffset) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        int idx = (int) (adjusted / sectorSize);
        return (idx >= 0 && idx < n) ? idx : -1;
    }

    private void drawSector(GuiGraphicsExtractor context, float cx, float cy, int index, int n, boolean hovered) {
        double sectorSize = 2 * Math.PI / n;
        double angleOffset = -Math.PI / 2 - sectorSize / 2.0;
        double baseAngle = angleOffset + sectorSize * index;
        double startAngle = baseAngle + GAP_HALF_ANGLE;
        double endAngle   = baseAngle + sectorSize - GAP_HALF_ANGLE;
        int color = hovered ? COLOR_SECTOR_HOVER : COLOR_SECTOR;
        fillSector(context, cx, cy, OUTER_RADIUS, startAngle, endAngle, color);
    }

    private void fillCircle(GuiGraphicsExtractor context, float cx, float cy, float radius, int color) {
        fillSector(context, cx, cy, radius, 0, 2 * Math.PI, color);
    }

    /**
     * Fills a pie sector using context.fill() — one call per pixel column.
     * Uses analytical cross-product tests to compute the y-range per column in O(1),
     * giving O(r) total instead of O(r²) with atan2.
     *
     * For a sector [startAngle, endAngle] with span < 2π:
     *   A point (dx, dy) is inside iff
     *     dy*cos(S) - dx*sin(S) >= 0   (left of start ray)
     *     dy*cos(E) - dx*sin(E) <= 0   (right of end ray)
     * Each constraint is linear in dy → gives yLo / yHi directly.
     */
    private void fillSector(GuiGraphicsExtractor context, float cx, float cy, float radius,
                            double startAngle, double endAngle, int color) {
        int r   = (int) Math.ceil(radius);
        int icx = (int) cx;
        int icy = (int) cy;

        double span = ((endAngle - startAngle) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        if (span >= 2 * Math.PI - 1e-9) {
            // Full circle — skip angular tests entirely
            for (int dx = -r; dx <= r; dx++) {
                int dMax = (int) Math.sqrt(Math.max(0.0, radius * radius - (double) dx * dx));
                if (dMax > 0) context.fill(icx + dx, icy - dMax, icx + dx + 1, icy + dMax + 1, color);
            }
            return;
        }

        double cosS = Math.cos(startAngle), sinS = Math.sin(startAngle);
        double cosE = Math.cos(endAngle),   sinE = Math.sin(endAngle);

        for (int dx = -r; dx <= r; dx++) {
            double r2 = radius * radius - (double) dx * dx;
            if (r2 <= 0) continue;
            int dyMax = (int) Math.sqrt(r2);
            double yLo = -dyMax, yHi = dyMax;

            // Start-ray constraint: dy*cosS - dx*sinS >= 0  →  dy >= dx*sinS/cosS
            double tS = (double) dx * sinS;
            if      (cosS >  1e-9) yLo = Math.max(yLo, tS / cosS);
            else if (cosS < -1e-9) yHi = Math.min(yHi, tS / cosS);
            else if (tS   >  1e-9) continue; // column entirely outside start ray

            // End-ray constraint: dy*cosE - dx*sinE <= 0  →  dy <= dx*sinE/cosE
            double tE = (double) dx * sinE;
            if      (cosE >  1e-9) yHi = Math.min(yHi, tE / cosE);
            else if (cosE < -1e-9) yLo = Math.max(yLo, tE / cosE);
            else if (tE   < -1e-9) continue; // column entirely outside end ray

            int fillY1 = (int) Math.ceil(yLo);
            int fillY2 = (int) Math.floor(yHi);
            if (fillY1 <= fillY2) {
                context.fill(icx + dx, icy + fillY1, icx + dx + 1, icy + fillY2 + 1, color);
            }
        }
    }

    private void drawSectorPreview(GuiGraphicsExtractor context, float cx, float cy, int index, int n) {
        double sectorSize = 2 * Math.PI / n;
        double angleOffset = -Math.PI / 2 - sectorSize / 2.0;
        double midAngle = angleOffset + sectorSize * index + sectorSize / 2.0;
        double previewDist = OUTER_RADIUS * 0.60;

        int px = (int) (cx + previewDist * Math.cos(midAngle));
        int py = (int) (cy + previewDist * Math.sin(midAngle));

        SkinEntry entry = entries.get(index);
        entry.ensureTextureLoaded();

        int halfW = 16;
        int halfH = 24;

        if (entry.textureId != null) {
            PlayerSkin skinTextures = new PlayerSkin(
                    new ClientAsset.DownloadedTexture(entry.textureId, ""), null, null,
                    entry.skinType == SkinType.SLIM ? PlayerModelType.SLIM : PlayerModelType.WIDE,
                    true
            );
            SkinRenderer.renderPlayer(context, px - halfW, py - halfH, px + halfW, py + halfH, halfH, skinTextures);
        }
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) { apply(); return true; }
        if (click.button() == 1) { onClose(); return true; }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (SimpleSkinSwapperClient.openWheelKey.matches(input)) {
            onClose();
            return true;
        }
        return super.keyReleased(input);
    }

    private void apply() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            SkinEntry entry = entries.get(selectedIndex);
            if (SkinSwapperState.beginSwap()) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                SkinChange.changeSkin(
                        entry.file,
                        entry.skinType,
                        entry.textureId,
                        () -> {
                            if (minecraft.player != null)
                                minecraft.player.sendOverlayMessage(
                                        Component.translatable("simpleskinswapper.message.success"));
                        },
                        err -> {
                            if (minecraft.player != null)
                                minecraft.player.sendOverlayMessage(
                                        Component.translatable("simpleskinswapper.message.error", err));
                        }
                );
                if (minecraft.player != null)
                    minecraft.player.sendOverlayMessage(
                            Component.translatable("simpleskinswapper.message.applying"));
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    // -------------------------------------------------------------------------
    // Data loading — reads order.txt, no writes
    // -------------------------------------------------------------------------

    private static List<SkinEntry> loadWheelEntries() {
        List<SkinEntry> ordered = SkinCarouselScreen.loadOrderedEntries();
        if (ordered.size() <= MAX_ENTRIES) return ordered;
        return new ArrayList<>(ordered.subList(0, MAX_ENTRIES));
    }
}
