package okuudra.api.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import kotlin.math.roundToInt

class ShapeElement(
    private val clip: ScreenRectangle?,
    private val box: ScreenRectangle,
    private val x0: Float,
    private val y0: Float,
    private val x1: Float,
    private val y1: Float,
    private val colors: IntArray,
    private val radius: Float,
    private val stroke: Float,
    private val half: FloatArray? = null,
    private val feather: Float = 0f,
) : GuiElementRenderState {
    override fun buildVertices(consumer: VertexConsumer) {
        val halfW = (x1 - x0) / 2f
        val halfH = (y1 - y0) / 2f
        val ex = halfW + FEATHER + feather * 0.5f
        val ey = halfH + FEATHER + feather * 0.5f
        val cx = x0 + halfW
        val cy = y0 + halfH
        vertex(consumer, cx - ex, cy - ey, -ex, -ey, colors[0])
        vertex(consumer, cx - ex, cy + ey, -ex, ey, colors[3])
        vertex(consumer, cx + ex, cy + ey, ex, ey, colors[2])
        vertex(consumer, cx + ex, cy - ey, ex, -ey, colors[1])
    }

    private fun vertex(c: VertexConsumer, x: Float, y: Float, lx: Float, ly: Float, argb: Int) {
        val arc = radius < 0f
        val u1 = if (arc) short(stroke * 4000f) else short(stroke * 16f)
        val v1 = short(feather * 16f)
        val u2 = if (arc) short((half?.get(0) ?: 0f) * 8f) else 0
        val v2 = if (arc) short((half?.get(1) ?: 0f) * 8f) else 0
        c.addVertex(x, y, radius)
            .setColor((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, (argb ushr 24) and 0xFF)
            .setUv(lx, ly)
            .setUv1(u1, v1)
            .setUv2(u2, v2)
    }

    private fun short(v: Float): Int = v.roundToInt().coerceIn(-32767, 32767)

    override fun pipeline(): RenderPipeline = UiPipelines.SHAPE

    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

    override fun scissorArea(): ScreenRectangle? = clip

    override fun bounds(): ScreenRectangle = box

    companion object {
        const val FEATHER = 1f
    }
}
