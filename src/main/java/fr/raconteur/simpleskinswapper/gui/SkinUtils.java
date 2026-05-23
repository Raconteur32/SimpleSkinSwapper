package fr.raconteur.simpleskinswapper.gui;

import com.mojang.blaze3d.platform.NativeImage;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class SkinUtils {

    /**
     * Detect slim vs classic by checking pixel (50, 19) alpha.
     * If alpha == 0x00, the skin is slim (alex model).
     * Adapted from SkinSwapper (net.cobrasrock.skinswapper.gui.SkinEntry).
     */
    public static SkinType detectSkinType(File skinFile) {
        try {
            BufferedImage image = ImageIO.read(skinFile);
            if (image == null) return SkinType.CLASSIC;
            int pixel = image.getRGB(50, 19);
            int alpha = (pixel >> 24) & 0xFF;
            return (alpha == 0x00) ? SkinType.SLIM : SkinType.CLASSIC;
        } catch (IOException e) {
            SimpleSkinSwapper.LOGGER.warn("Failed to detect skin type for {}: {}", skinFile.getName(), e.getMessage());
            return SkinType.CLASSIC;
        }
    }

    /**
     * Load a skin file as a Minecraft GPU texture.
     * Handles 64x32 → 64x64 remapping if needed.
     * Returns the registered Identifier, or null on failure.
     */
    public static Identifier loadSkinTexture(File skinFile, String textureId) {
        try (InputStream is = Files.newInputStream(skinFile.toPath())) {
            NativeImage raw = NativeImage.read(is);
            NativeImage image = raw;

            // Remap 64x32 skins to 64x64
            if (raw.getHeight() == 32) {
                image = remapTexture(raw);
                raw.close();
            }

            Identifier id = Identifier.fromNamespaceAndPath(SimpleSkinSwapper.MOD_ID, textureId);
            Minecraft client = Minecraft.getInstance();
            DynamicTexture texture = new DynamicTexture(
                    () -> SimpleSkinSwapper.MOD_ID + ":" + textureId, image);
            client.getTextureManager().register(id, texture);
            return id;
        } catch (IOException e) {
            SimpleSkinSwapper.LOGGER.error("Failed to load skin texture from {}: {}", skinFile.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Load a skin file as a Minecraft GPU texture from a thread (schedules on render thread).
     */
    public static void loadSkinTextureAsync(File skinFile, String textureId, java.util.function.Consumer<Identifier> callback) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Identifier id = loadSkinTexture(skinFile, textureId);
            if (id != null) callback.accept(id);
        });
    }

    /**
     * Remap a 64x32 legacy skin to 64x64 format.
     * Copies and mirrors limbs into the new bottom half.
     * Adapted from SkinSwapper (net.cobrasrock.skinswapper.gui.SkinUtils).
     */
    private static NativeImage remapTexture(NativeImage src) {
        NativeImage dst = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);

        // Copy top half (0-31) as-is
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 32; y++) {
                dst.setPixel(x, y, src.getPixel(x, y));
            }
        }

        // Strip alpha from base layers
        stripAlpha(dst, 0, 0, 32, 16);

        // Mirror right leg → left leg (16,48)
        copyMirroredLimb(dst, 0, 16, 16, 48, 16, 16);
        // Mirror right arm → left arm (32,48)
        copyMirroredLimb(dst, 40, 16, 32, 48, 16, 16);

        return dst;
    }

    private static void stripAlpha(NativeImage img, int x0, int y0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int y = y0; y < y0 + h; y++) {
                int color = img.getPixel(x, y);
                img.setPixel(x, y, color | 0xFF000000);
            }
        }
    }

    private static void copyMirroredLimb(NativeImage img, int srcX, int srcY, int dstX, int dstY, int w, int h) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int color = img.getPixel(srcX + (w - 1 - x), srcY + y);
                img.setPixel(dstX + x, dstY + y, color);
            }
        }
    }
}
