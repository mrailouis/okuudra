package okuudra.api.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState

class GlyphElement(
    private val texture: TextureSetup,
    private val clip: ScreenRectangle?,
    private val box: ScreenRectangle,
    private val quads: FloatArray,
    private val colors: IntArray,
    private val count: Int,
    private val rightColors: IntArray = colors,
) : GuiElementRenderState {
    override fun buildVertices(consumer: VertexConsumer) {
        for (i in 0 until count) {
            val q = i * 8
            val argb = colors[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val a = (argb ushr 24) and 0xFF
            val right = rightColors[i]
            val rr = (right shr 16) and 0xFF
            val rg = (right shr 8) and 0xFF
            val rb = right and 0xFF
            val ra = (right ushr 24) and 0xFF
            val x0 = quads[q]
            val y0 = quads[q + 1]
            val x1 = quads[q + 2]
            val y1 = quads[q + 3]
            val u0 = quads[q + 4]
            val v0 = quads[q + 5]
            val u1 = quads[q + 6]
            val v1 = quads[q + 7]
            consumer.addVertex(x0, y0, 0f).setUv(u0, v0).setColor(r, g, b, a)
            consumer.addVertex(x0, y1, 0f).setUv(u0, v1).setColor(r, g, b, a)
            consumer.addVertex(x1, y1, 0f).setUv(u1, v1).setColor(rr, rg, rb, ra)
            consumer.addVertex(x1, y0, 0f).setUv(u1, v0).setColor(rr, rg, rb, ra)
        }
    }

    override fun pipeline(): RenderPipeline = RenderPipelines.GUI_TEXTURED

    override fun textureSetup(): TextureSetup = texture

    override fun scissorArea(): ScreenRectangle? = clip

    override fun bounds(): ScreenRectangle = box
}
