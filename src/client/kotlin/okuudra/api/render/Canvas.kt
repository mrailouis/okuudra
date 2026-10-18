package okuudra.api.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import okuudra.api.util.Argb
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class Canvas {
    private var extractor: GuiGraphicsExtractor? = null
    private var state: GuiRenderState? = null
    var guiScale = 1f
        private set
    var width = 0f
        private set
    var height = 0f
        private set

    val layoutWidth: Float get() = width / sc
    val layoutHeight: Float get() = height / sc

    private var ox = 0f
    private var oy = 0f
    private var sc = 1f
    private var alpha = 1f
    private val saved = ArrayDeque<FloatArray>()
    private val clips = ArrayDeque<ScreenRectangle>()
    private val runs = LinkedHashMap<GpuTextureView, GlyphRun>()

    fun begin(extractor: GuiGraphicsExtractor, guiScale: Float, width: Int, height: Int) {
        this.extractor = extractor
        this.state = extractor.guiRenderState
        this.guiScale = guiScale
        this.width = width.toFloat()
        this.height = height.toFloat()
        ox = 0f
        oy = 0f
        sc = 1f
        alpha = 1f
        saved.clear()
        clips.clear()
    }

    fun end() {
        state = null
        extractor = null
    }

    fun push() = saved.addLast(floatArrayOf(ox, oy, sc, alpha))

    fun pop() {
        val s = saved.removeLastOrNull() ?: return
        ox = s[0]
        oy = s[1]
        sc = s[2]
        alpha = s[3]
    }

    fun translate(dx: Float, dy: Float) {
        ox += dx * sc
        oy += dy * sc
    }

    fun scale(s: Float) {
        sc *= s
    }

    fun alpha(a: Float) {
        alpha = a.coerceIn(0f, 1f)
    }

    fun clip(x: Float, y: Float, w: Float, h: Float) {
        val window = Minecraft.getInstance().window
        val rightLimit = minOf(width, floor(window.width / guiScale).toFloat()).toInt()
        val bottomLimit = minOf(height, floor(window.height / guiScale).toFloat()).toInt()
        val parent = clips.lastOrNull() ?: ScreenRectangle(0, 0, rightLimit, bottomLimit)
        val left = floor(tx(x)).toInt().coerceIn(parent.left(), parent.right())
        val top = floor(ty(y)).toInt().coerceIn(parent.top(), parent.bottom())
        val right = ceil(tx(x + w)).toInt().coerceIn(left, parent.right())
        val bottom = ceil(ty(y + h)).toInt().coerceIn(top, parent.bottom())
        val next = ScreenRectangle(left, top, right - left, bottom - top)
        clips.addLast(next)
        extractor?.enableScissor(next.left(), next.top(), next.right(), next.bottom())
    }

    fun unclip() {
        clips.removeLastOrNull()
        extractor?.disableScissor()
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f) =
        emit(x, y, w, h, intArrayOf(color, color, color, color), radius, 0f)

    fun border(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f, stroke: Float = 1f) =
        emit(x, y, w, h, intArrayOf(color, color, color, color), radius, stroke * sc)

    fun gradient4(x: Float, y: Float, w: Float, h: Float, tl: Int, tr: Int, br: Int, bl: Int, radius: Float = 0f) =
        emit(x, y, w, h, intArrayOf(tl, tr, br, bl), radius, 0f)

    fun hline(x: Float, y: Float, w: Float, color: Int) = rect(x, y, w, 1f, color)

    fun vline(x: Float, y: Float, h: Float, color: Int) = rect(x, y, 1f, h, color)

    fun circle(cx: Float, cy: Float, r: Float, color: Int) = rect(cx - r, cy - r, r * 2f, r * 2f, color, r)

    fun ring(cx: Float, cy: Float, r: Float, color: Int, stroke: Float = 1f) = border(cx - r, cy - r, r * 2f, r * 2f, color, r, stroke)

    fun shadow(x: Float, y: Float, w: Float, h: Float, radius: Float, blur: Float, color: Int) {
        val st = state ?: return
        if (w <= 0f || h <= 0f) return
        val feather = maxOf(1f, blur)
        val x0 = tx(x)
        val y0 = ty(y + feather * 0.5f)
        val x1 = x0 + w * sc
        val y1 = y0 + h * sc
        val pad = ShapeElement.FEATHER + feather * sc * 0.5f
        val box = bounds(x0 - pad, y0 - pad, x1 + pad, y1 + pad) ?: return
        val c = Argb.scaleAlpha(color, alpha)
        st.addGuiElement(ShapeElement(clips.lastOrNull(), box, x0, y0, x1, y1, intArrayOf(c, c, c, c), (radius + feather * 0.5f) * sc, 0f, null, feather * sc))
    }

    fun arc(cx: Float, cy: Float, inner: Float, outer: Float, startDeg: Float, sweepDeg: Float, color: Int) {
        val st = state ?: return
        if (outer <= 0f || sweepDeg <= 0f) return
        val x0 = tx(cx - outer)
        val y0 = ty(cy - outer)
        val x1 = tx(cx + outer)
        val y1 = ty(cy + outer)
        val box = bounds(x0 - ShapeElement.FEATHER, y0 - ShapeElement.FEATHER, x1 + ShapeElement.FEATHER, y1 + ShapeElement.FEATHER) ?: return
        val c = Argb.scaleAlpha(color, alpha)
        val sweep = Math.toRadians(sweepDeg.toDouble()).toFloat()
        val start = Math.toRadians(startDeg.toDouble()).toFloat()
        st.addGuiElement(
            ShapeElement(clips.lastOrNull(), box, x0, y0, x1, y1, intArrayOf(c, c, c, c), -(1f + sweep), start, floatArrayOf(inner * sc, outer * sc)),
        )
    }

    private fun emit(x: Float, y: Float, w: Float, h: Float, colors: IntArray, radius: Float, stroke: Float) {
        val st = state ?: return
        if (w <= 0f || h <= 0f) return
        val x0 = tx(x)
        val y0 = ty(y)
        val x1 = x0 + w * sc
        val y1 = y0 + h * sc
        val box = bounds(x0 - ShapeElement.FEATHER, y0 - ShapeElement.FEATHER, x1 + ShapeElement.FEATHER, y1 + ShapeElement.FEATHER) ?: return
        val faded = IntArray(4) { Argb.scaleAlpha(colors[it], alpha) }
        st.addGuiElement(ShapeElement(clips.lastOrNull(), box, x0, y0, x1, y1, faded, radius * sc, stroke))
    }

    fun text(font: UiFont, text: String, x: Float, y: Float, size: Float, color: Int, shadow: Boolean = false, colorAt: ((Float) -> Int)? = null): Float {
        if (shadow) {
            val off = (size / 12f).coerceAtLeast(0.5f)
            glyphs(font, text, x + off, y + off, size, Argb.withAlpha(0x000000, (Argb.alpha(color) * 0.55f).roundToInt()))
        }
        return glyphs(font, text, x, y, size, color, colorAt)
    }

    private fun glyphs(font: UiFont, text: String, x: Float, y: Float, size: Float, color: Int, colorAt: ((Float) -> Int)? = null): Float {
        val st = state ?: return 0f
        val atlas = Fonts.atlas(font) ?: return 0f
        if (text.isEmpty()) return 0f
        val physical = size * sc * guiScale
        val bucket = FontAtlas.bucket(physical)
        val k = (physical / bucket) / guiScale
        val argb = Argb.scaleAlpha(color, alpha)
        val baseline = ty(y) + atlas.ascent(bucket) * k
        var pen = tx(x)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val g = atlas.glyph(cp, bucket) ?: continue
            if (g.width > 0 && g.height > 0) {
                val gx = snap(pen + g.bearingX * k)
                val gy = snap(baseline + g.bearingY * k)
                val run = runs.getOrPut(atlas.texture(g.page)) { GlyphRun() }
                val left = colorAt?.let { Argb.scaleAlpha(it((gx - tx(x)) / sc), alpha) } ?: argb
                val right = colorAt?.let { Argb.scaleAlpha(it((gx + g.width * k - tx(x)) / sc), alpha) } ?: argb
                run.add(gx, gy, gx + g.width * k, gy + g.height * k, g.u0, g.v0, g.u1, g.v1, left, right)
            }
            pen += g.advance * k
        }
        flushRuns(st)
        return (pen - tx(x)) / sc
    }

    fun textWidth(font: UiFont, text: String, size: Float): Float {
        val atlas = Fonts.atlas(font) ?: return 0f
        val physical = size * sc * guiScale
        val bucket = FontAtlas.bucket(physical)
        val k = (physical / bucket) / guiScale
        var w = 0f
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            w += (atlas.glyph(cp, bucket)?.advance ?: 0f) * k
        }
        return w / sc
    }

    fun textCentered(font: UiFont, text: String, cx: Float, y: Float, size: Float, color: Int, shadow: Boolean = false) =
        text(font, text, cx - textWidth(font, text, size) / 2f, y, size, color, shadow)

    fun textRight(font: UiFont, text: String, right: Float, y: Float, size: Float, color: Int, shadow: Boolean = false) =
        text(font, text, right - textWidth(font, text, size), y, size, color, shadow)

    fun trim(font: UiFont, text: String, size: Float, maxWidth: Float): String {
        if (textWidth(font, text, size) <= maxWidth) return text
        var end = text.length
        while (end > 0 && textWidth(font, text.substring(0, end) + "…", size) > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    fun wrap(font: UiFont, text: String, size: Float, maxWidth: Float): List<String> {
        val lines = ArrayList<String>()
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isEmpty() || textWidth(font, candidate, size) <= maxWidth) {
                current = candidate
            } else {
                lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    fun mcText(text: String, x: Float, y: Float, color: Int = Argb.WHITE, shadow: Boolean = true): Float {
        val ex = extractor ?: return 0f
        val pose = ex.pose()
        pose.pushMatrix()
        pose.translate(tx(x), ty(y))
        pose.scale(sc, sc)
        ex.text(Minecraft.getInstance().font, text, 0, 0, Argb.scaleAlpha(color, alpha), shadow)
        pose.popMatrix()
        return mcTextWidth(text)
    }

    fun mcTextWidth(text: String): Float = Minecraft.getInstance().font.width(text).toFloat()

    val mcLineHeight: Float get() = Minecraft.getInstance().font.lineHeight.toFloat()

    fun item(stack: ItemStack, x: Float, y: Float, size: Float = 16f) {
        val ex = extractor ?: return
        val pose = ex.pose()
        pose.pushMatrix()
        pose.translate(tx(x), ty(y))
        pose.scale(sc * size / 16f, sc * size / 16f)
        ex.item(stack, 0, 0)
        pose.popMatrix()
    }

    fun physicalSize(size: Float) = ceil(size * sc * guiScale).toInt().coerceAtLeast(1)

    fun image(view: GpuTextureView, x: Float, y: Float, w: Float, h: Float, color: Int = -1) {
        val st = state ?: return
        val x0 = snap(tx(x))
        val y0 = snap(ty(y))
        val x1 = x0 + physicalSize(w) / guiScale
        val y1 = y0 + physicalSize(h) / guiScale
        val box = bounds(x0, y0, x1, y1) ?: return
        val quads = floatArrayOf(x0, y0, x1, y1, 0f, 0f, 1f, 1f)
        val setup = TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
        st.addGuiElement(GlyphElement(setup, clips.lastOrNull(), box, quads, intArrayOf(Argb.scaleAlpha(color, alpha)), 1))
    }

    private fun tx(x: Float) = ox + x * sc

    private fun ty(y: Float) = oy + y * sc

    private fun snap(v: Float) = (v * guiScale).roundToInt() / guiScale

    private fun bounds(x0: Float, y0: Float, x1: Float, y1: Float): ScreenRectangle? {
        val rect = ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1) - Mth.floor(x0), Mth.ceil(y1) - Mth.floor(y0))
        val clip = clips.lastOrNull() ?: return rect
        return clip.intersection(rect)
    }

    private fun flushRuns(st: GuiRenderState) {
        for ((view, run) in runs) {
            if (run.count == 0) continue
            val box = bounds(run.minX, run.minY, run.maxX, run.maxY)
            if (box != null) {
                val setup = TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                st.addGuiElement(
                    GlyphElement(
                        setup,
                        clips.lastOrNull(),
                        box,
                        run.quads.copyOf(run.count * 8),
                        run.colors.copyOf(run.count),
                        run.count,
                        run.rightColors.copyOf(run.count),
                    ),
                )
            }
            run.reset()
        }
    }

    private class GlyphRun {
        var quads = FloatArray(64 * 8)
        var colors = IntArray(64)
        var rightColors = IntArray(64)
        var count = 0
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        fun add(x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float, argb: Int, right: Int = argb) {
            if (count == colors.size) {
                quads = quads.copyOf(quads.size * 2)
                colors = colors.copyOf(colors.size * 2)
                rightColors = rightColors.copyOf(rightColors.size * 2)
            }
            val q = count * 8
            quads[q] = x0
            quads[q + 1] = y0
            quads[q + 2] = x1
            quads[q + 3] = y1
            quads[q + 4] = u0
            quads[q + 5] = v0
            quads[q + 6] = u1
            quads[q + 7] = v1
            colors[count] = argb
            rightColors[count] = right
            count++
            minX = minOf(minX, x0)
            minY = minOf(minY, y0)
            maxX = maxOf(maxX, x1)
            maxY = maxOf(maxY, y1)
        }

        fun reset() {
            count = 0
            minX = Float.MAX_VALUE
            minY = Float.MAX_VALUE
            maxX = -Float.MAX_VALUE
            maxY = -Float.MAX_VALUE
        }
    }
}
