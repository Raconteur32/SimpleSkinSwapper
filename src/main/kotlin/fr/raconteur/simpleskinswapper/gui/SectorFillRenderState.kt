package fr.raconteur.simpleskinswapper.gui

//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline
*///?} else {
import com.mojang.blaze3d.pipeline.RenderPipeline
//?}
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Flat-color pie sector (triangle fan) GUI element, replacing the old one-[net.minecraft.client.gui.GuiGraphicsExtractor.fill]-per-pixel-column
 * approach with a single mesh: one draw submission per sector instead of O(radius) quads.
 *
 * The GUI pipeline only supports quads, so each fan triangle is emitted as a degenerate quad
 * (center, p1, p0, p0) which the quad indexer splits into the wanted triangle plus a zero-area one.
 * The GUI pipeline keeps back-face culling enabled, so the two arc points are emitted in
 * decreasing-angle order to match the front-face winding of vanilla's ColoredRectangleRenderState.
 */
class SectorFillRenderState(
    private val pose: Matrix3x2fc,
    private val cx: Float,
    private val cy: Float,
    private val radius: Float,
    private val startAngle: Float,
    private val endAngle: Float,
    private val color: Int,
    private val scissor: ScreenRectangle?,
    private val boundsRect: ScreenRectangle?
) : GuiElementRenderState {

    constructor(
        pose: Matrix3x2fc, cx: Float, cy: Float, radius: Float,
        startAngle: Float, endAngle: Float, color: Int, scissor: ScreenRectangle?
    ) : this(pose, cx, cy, radius, startAngle, endAngle, color, scissor,
        computeBounds(pose, cx, cy, radius, startAngle, endAngle, scissor))

    override fun buildVertices(consumer: VertexConsumer) {
        val segments = segmentCount(radius, endAngle - startAngle)
        val span = endAngle - startAngle
        for (i in 0..<segments) {
            val a0 = startAngle + span * i / segments
            val a1 = startAngle + span * (i + 1) / segments
            val x0 = cx + radius * cos(a0)
            val y0 = cy + radius * sin(a0)
            val x1 = cx + radius * cos(a1)
            val y1 = cy + radius * sin(a1)
            consumer.addVertexWith2DPose(pose, cx, cy).setColor(color)
            consumer.addVertexWith2DPose(pose, x1, y1).setColor(color)
            consumer.addVertexWith2DPose(pose, x0, y0).setColor(color)
            consumer.addVertexWith2DPose(pose, x0, y0).setColor(color)
        }
    }

    override fun pipeline(): RenderPipeline = RenderPipelines.GUI

    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

    override fun scissorArea(): ScreenRectangle? = scissor

    override fun bounds(): ScreenRectangle? = boundsRect

    companion object {
        /** ~3 px of arc per segment, clamped: smooth enough at the wheel radius, cheap for small discs. */
        private fun segmentCount(radius: Float, span: Float): Int =
            (radius * span / 3.0f).toInt().coerceIn(8, 64)

        private fun computeBounds(
            pose: Matrix3x2fc, cx: Float, cy: Float, radius: Float,
            startAngle: Float, endAngle: Float, scissor: ScreenRectangle?
        ): ScreenRectangle? {
            val segments = segmentCount(radius, endAngle - startAngle)
            val span = endAngle - startAngle
            var minX = cx
            var maxX = cx
            var minY = cy
            var maxY = cy
            for (i in 0..segments) {
                val a = startAngle + span * i / segments
                val x = cx + radius * cos(a)
                val y = cy + radius * sin(a)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            val x0 = floor(minX).toInt()
            val y0 = floor(minY).toInt()
            val bounds = ScreenRectangle(x0, y0, ceil(maxX).toInt() - x0, ceil(maxY).toInt() - y0)
                .transformMaxBounds(pose)
            return scissor?.intersection(bounds) ?: bounds
        }
    }
}
