package fr.raconteur.simpleskinswapper.gui

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Matrix3x2f
import java.io.File
import com.mojang.blaze3d.ProjectionType
//? if >=26.2 {
import com.mojang.blaze3d.GpuFormat
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.renderer.SubmitNodeStorage
//?} else {
/*import com.mojang.blaze3d.textures.TextureFormat
*///?}
//? if >=26.1 {
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.state.level.CameraRenderState
//?} else {
/*import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.state.CameraRenderState
*///?}

/**
 * Global cache of baked skin-preview textures, keyed by skin file identity.
 *
 * Each preview is rendered once into an offscreen texture (mirroring vanilla's
 * picture-in-picture pipeline, same framing constants as [SkinRenderer]) and then drawn as a
 * cheap blit each frame. Baking is progressive: [processBakeQueue] runs at the head of
 * `GuiRenderer.render` (via mixin — the same GPU/lighting conditions as vanilla PiP prepare)
 * and bakes at most [MAX_BAKES_PER_FRAME] previews per frame.
 *
 * Shared outside any screen instance so multiple simultaneously visible wheels reuse the same
 * baked textures.
 */
object SkinPreviewCache {

    /** Baked texture size: 2x supersample of the 32x48 logical preview box. */
    const val TEXTURE_WIDTH = 64
    const val TEXTURE_HEIGHT = 96

    private const val MAX_BAKES_PER_FRAME = 2

    /** Logical half-height of the preview box the texture stands for (matches the wheel's 32x48 box). */
    private const val LOGICAL_HALF_HEIGHT = 24.0f

    // Texture usage flags, copied from vanilla's PictureInPictureRenderer (differ between versions).
    //? if >=26.1 {
    private const val COLOR_USAGE = 13
    private const val DEPTH_USAGE = 9
    //?} else {
    /*private const val COLOR_USAGE = 12
    private const val DEPTH_USAGE = 8
    *///?}

    private class BakedPreview(
        val texture: GpuTexture,
        val textureView: GpuTextureView,
        val depthTexture: GpuTexture,
        val depthTextureView: GpuTextureView
    ) {
        fun close() {
            texture.close()
            textureView.close()
            depthTexture.close()
            depthTextureView.close()
        }
    }

    private val baked = HashMap<String, BakedPreview>()
    private val queue = LinkedHashMap<String, PlayerSkin>()

    //? if >=26.2 {
    private val submitNodeStorage = SubmitNodeStorage()
    //?}
    //? if >=26.1 {
    private val projection = Projection()
    private val projectionMatrixBuffer = ProjectionMatrixBuffer("SkinPreviewCache")
    //?} else {
    /*private val projectionMatrixBuffer = CachedOrthoProjectionMatrixBuffer("SkinPreviewCache", -1000.0f, 1000.0f, true)
    *///?}

    /** Cache key for a skin file; changes if the file is replaced (mtime) so stale bakes are dropped. */
    fun previewKey(file: File): String = "${file.absolutePath}:${file.lastModified()}"

    /**
     * Enqueue a bake for [key] if not already baked/queued. [skin] is only built when actually
     * enqueueing, so baked previews cost one map lookup per frame.
     */
    fun requestPreview(key: String, skin: () -> PlayerSkin) {
        if (baked.containsKey(key) || queue.containsKey(key)) return
        queue[key] = skin()
    }

    /** Drop baked/queued previews whose key is not in [keys], releasing their GPU textures. */
    fun retainOnly(keys: Set<String>) {
        val it = baked.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.key !in keys) {
                entry.value.close()
                it.remove()
            }
        }
        queue.keys.retainAll(keys)
    }

    /**
     * Draw the baked preview for [key] into the box. Returns false (draws nothing) when the
     * preview is not baked yet.
     *
     * Submitted through addGuiElement with real bounds (unlike vanilla PiP blits, which pass
     * null bounds to addBlitToCurrentLayer): the GUI render state's bounds-based layering then
     * places each preview above the overlapping previews submitted before it — the painter's
     * order the wheel relies on — and keeps the hovered live PiP at its own painter position.
     * (addBlitToCurrentLayer would pile every blit into the current node, where the per-texture
     * sort key would scramble the order.)
     */
    fun submitPreviewBlit(context: GuiGraphicsExtractor, key: String, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        val view = baked[key]?.textureView ?: return false
        val pose = Matrix3x2f(context.pose())
        val scissor = context.scissorStack.peek()
        val rect = ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose)
        val bounds = scissor?.intersection(rect) ?: rect
        context.guiRenderState.addGuiElement(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                pose,
                x0, y0, x1, y1,
                0.0f, 1.0f, 1.0f, 0.0f, -1,
                scissor, bounds
            )
        )
        return true
    }

    /**
     * Bake up to [MAX_BAKES_PER_FRAME] queued previews. Called every frame from the head of
     * `GuiRenderer.render` (see GuiRendererMixin), where lighting/projection conditions match
     * vanilla's picture-in-picture prepare.
     */
    @JvmStatic
    fun processBakeQueue() {
        if (queue.isEmpty()) return
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        var count = 0
        val it = queue.entries.iterator()
        while (it.hasNext() && count < MAX_BAKES_PER_FRAME) {
            val entry = it.next()
            it.remove()
            bake(entry.key, entry.value)
            count++
        }
    }

    /**
     * Render [skin]'s avatar once into a fresh offscreen texture and cache it under [key].
     * Mirrors vanilla's PictureInPictureRenderer.prepare + GuiEntityRenderer.renderToTexture,
     * with the texture standing for the whole 32x48 logical preview box at 2x resolution.
     */
    private fun bake(key: String, skin: PlayerSkin) {
        val mc = Minecraft.getInstance()
        val device = RenderSystem.getDevice()

        //? if >=26.2 {
        val colorTexture = device.createTexture({ "SimpleSkinSwapper skin preview" }, COLOR_USAGE, GpuFormat.RGBA8_UNORM, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1, 1)
        val depthTexture = device.createTexture({ "SimpleSkinSwapper skin preview depth" }, DEPTH_USAGE, GpuFormat.D32_FLOAT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1, 1)
        //?} else {
        /*val colorTexture = device.createTexture({ "SimpleSkinSwapper skin preview" }, COLOR_USAGE, TextureFormat.RGBA8, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1, 1)
        val depthTexture = device.createTexture({ "SimpleSkinSwapper skin preview depth" }, DEPTH_USAGE, TextureFormat.DEPTH32, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1, 1)
        *///?}
        val colorView = device.createTextureView(colorTexture)
        val depthView = device.createTextureView(depthTexture)
        val preview = BakedPreview(colorTexture, colorView, depthTexture, depthView)

        RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView
        try {
            //? if >=26.2 {
            device.createCommandEncoder().clearColorAndDepthTextures(colorTexture, GuiRenderer.CLEAR_COLOR, depthTexture, 0.0)
            //?} else {
            /*device.createCommandEncoder().clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0)
            *///?}
            //? if >=26.1 {
            projection.setupOrtho(-1000.0f, 1000.0f, TEXTURE_WIDTH.toFloat(), TEXTURE_HEIGHT.toFloat(), true)
            RenderSystem.setProjectionMatrix(projectionMatrixBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC)
            //?} else {
            /*RenderSystem.setProjectionMatrix(projectionMatrixBuffer.getBuffer(TEXTURE_WIDTH.toFloat(), TEXTURE_HEIGHT.toFloat()), ProjectionType.ORTHOGRAPHIC)
            *///?}
            //? if >=26.2 {
            val modelViewStack = RenderSystem.getModelViewStack()
            modelViewStack.pushMatrix()
            //?}

            val poseStack = PoseStack()
            poseStack.translate(TEXTURE_WIDTH / 2.0f, TEXTURE_HEIGHT / 2.0f, 0.0f)
            val scale = (TEXTURE_WIDTH / 32.0f) * (LOGICAL_HALF_HEIGHT / SkinRenderer.PREVIEW_HALF_HEIGHT)
            poseStack.scale(scale, scale, -scale)

            //? if >=26.2 {
            mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
            //?} else {
            /*mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
            *///?}
            poseStack.translate(SkinRenderer.MODEL_OFFSET.x(), SkinRenderer.MODEL_OFFSET.y(), SkinRenderer.MODEL_OFFSET.z())
            poseStack.mulPose(SkinRenderer.BASE_ROTATION)

            val cameraState = CameraRenderState()
            val avatarState = SkinRenderer.buildRenderState(skin)
            //? if >=26.2 {
            mc.entityRenderDispatcher.submit(avatarState, cameraState, 0.0, 0.0, 0.0, poseStack, submitNodeStorage)
            mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(submitNodeStorage)
            modelViewStack.popMatrix()
            //?} else {
            /*val featureRenderDispatcher = mc.gameRenderer.getFeatureRenderDispatcher()
            mc.entityRenderDispatcher.submit(avatarState, cameraState, 0.0, 0.0, 0.0, poseStack, featureRenderDispatcher.getSubmitNodeStorage())
            featureRenderDispatcher.renderAllFeatures()
            mc.renderBuffers().bufferSource().endBatch()
            *///?}
        } catch (t: Throwable) {
            preview.close()
            SimpleSkinSwapper.LOGGER.warn("Failed to bake skin preview for {}", key, t)
            return
        } finally {
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
        }
        baked[key] = preview
    }
}
