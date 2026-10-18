package okuudra.api.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class FontAtlas(bytes: ByteArray) {
    class Glyph(
        val page: Int,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val width: Int,
        val height: Int,
        val bearingX: Int,
        val bearingY: Int,
        val advance: Float,
    )

    private class Page {
        val bitmap: ByteBuffer = MemoryUtil.memCalloc(PAGE * PAGE * 4)
        var penX = 0
        var penY = 0
        var rowHeight = 0
        var dirtyTop = PAGE
        var dirtyBottom = 0
        private var texture: GpuTexture? = null
        private var view: GpuTextureView? = null

        fun reserve(w: Int, h: Int): IntArray? {
            if (penX + w + PAD > PAGE) {
                penX = 0
                penY += rowHeight + PAD
                rowHeight = 0
            }
            if (penY + h + PAD > PAGE) return null
            val out = intArrayOf(penX, penY)
            penX += w + PAD
            rowHeight = maxOf(rowHeight, h)
            return out
        }

        fun touch(top: Int, bottom: Int) {
            dirtyTop = minOf(dirtyTop, top)
            dirtyBottom = maxOf(dirtyBottom, bottom)
        }

        fun view(): GpuTextureView {
            val device = RenderSystem.getDevice()
            val tex = texture ?: device.createTexture(
                { "okuudra glyph page" },
                GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8,
                PAGE,
                PAGE,
                1,
                1,
            ).also {
                texture = it
                touch(0, PAGE)
            }
            return view ?: device.createTextureView(tex).also { view = it }
        }

        fun upload() {
            val tex = texture ?: return
            if (dirtyTop >= dirtyBottom) return
            val rows = bitmap.duplicate()
            rows.limit(dirtyBottom * PAGE * 4).position(dirtyTop * PAGE * 4)
            RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(tex, rows.slice(), NativeImage.Format.RGBA, 0, 0, 0, dirtyTop, PAGE, dirtyBottom - dirtyTop)
            dirtyTop = PAGE
            dirtyBottom = 0
        }

        fun destroy() {
            view?.close()
            texture?.close()
            MemoryUtil.memFree(bitmap)
        }
    }

    private val buffer: ByteBuffer = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
    private val info: STBTTFontinfo = STBTTFontinfo.create()
    private val ascentUnits: Int
    private var pages = ArrayList<Page>()
    private var retired = emptyList<Page>()
    private val glyphs = HashMap<Long, Glyph?>()
    private var full = false

    init {
        check(STBTruetype.stbtt_InitFont(info, buffer)) { "stb_truetype could not parse font" }
        MemoryStack.stackPush().use { stack ->
            val ascent = stack.mallocInt(1)
            val descent = stack.mallocInt(1)
            val gap = stack.mallocInt(1)
            STBTruetype.stbtt_GetFontVMetrics(info, ascent, descent, gap)
            ascentUnits = ascent.get(0)
        }
    }

    fun ascent(pixelSize: Float): Float = ascentUnits * STBTruetype.stbtt_ScaleForPixelHeight(info, pixelSize)

    fun texture(page: Int): GpuTextureView = pages[page].view()

    fun glyph(codePoint: Int, pixelSize: Float): Glyph? {
        val key = (codePoint.toLong() shl 16) or (pixelSize / STEP).roundToInt().toLong()
        glyphs[key]?.let { return it }
        if (key in glyphs) return null
        val glyph = rasterize(codePoint, pixelSize)
        if (glyph != null || !full) glyphs[key] = glyph
        return glyph
    }

    fun beginFrame() {
        if (!full) return
        retired.forEach { it.destroy() }
        retired = pages
        pages = ArrayList()
        glyphs.clear()
        full = false
    }

    fun upload() = pages.forEach { it.upload() }

    private fun rasterize(codePoint: Int, pixelSize: Float): Glyph? {
        val index = STBTruetype.stbtt_FindGlyphIndex(info, codePoint)
        if (index == 0) return null
        val scale = STBTruetype.stbtt_ScaleForPixelHeight(info, pixelSize)
        MemoryStack.stackPush().use { stack ->
            val adv = stack.mallocInt(1)
            val lsb = stack.mallocInt(1)
            STBTruetype.stbtt_GetGlyphHMetrics(info, index, adv, lsb)
            val advance = adv.get(0) * scale
            val x0 = stack.mallocInt(1)
            val y0 = stack.mallocInt(1)
            val x1 = stack.mallocInt(1)
            val y1 = stack.mallocInt(1)
            STBTruetype.stbtt_GetGlyphBitmapBox(info, index, scale, scale, x0, y0, x1, y1)
            val w = x1.get(0) - x0.get(0)
            val h = y1.get(0) - y0.get(0)
            if (w <= 0 || h <= 0) return Glyph(0, 0f, 0f, 0f, 0f, 0, 0, 0, 0, advance)
            val (page, pos) = reserve(w, h) ?: return null
            val coverage = MemoryUtil.memAlloc(w * h)
            STBTruetype.stbtt_MakeGlyphBitmap(info, coverage, w, h, w, scale, scale, index)
            val bitmap = page.bitmap
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val dst = ((pos[1] + row) * PAGE + pos[0] + col) * 4
                    bitmap.put(dst, 0xFF.toByte())
                    bitmap.put(dst + 1, 0xFF.toByte())
                    bitmap.put(dst + 2, 0xFF.toByte())
                    bitmap.put(dst + 3, coverage.get(row * w + col))
                }
            }
            MemoryUtil.memFree(coverage)
            page.touch(pos[1], pos[1] + h)
            return Glyph(
                pages.indexOf(page),
                pos[0] / PAGE.toFloat(),
                pos[1] / PAGE.toFloat(),
                (pos[0] + w) / PAGE.toFloat(),
                (pos[1] + h) / PAGE.toFloat(),
                w,
                h,
                x0.get(0),
                y0.get(0),
                advance,
            )
        }
    }

    private fun reserve(w: Int, h: Int): Pair<Page, IntArray>? {
        if (w + PAD > PAGE || h + PAD > PAGE) return null
        pages.lastOrNull()?.let { page -> page.reserve(w, h)?.let { return page to it } }
        if (pages.size >= MAX_PAGES) {
            full = true
            return null
        }
        val page = Page()
        pages.add(page)
        return page to (page.reserve(w, h) ?: return null)
    }

    fun destroy() {
        pages.forEach { it.destroy() }
        retired.forEach { it.destroy() }
        pages.clear()
        retired = emptyList()
        glyphs.clear()
    }

    companion object {
        private const val PAGE = 1024
        private const val PAD = 1
        private const val MAX_PAGES = 6
        private const val STEP = 0.5f
        const val MIN_SIZE = 4f
        const val MAX_SIZE = 128f

        fun bucket(pixelSize: Float): Float = ((pixelSize.coerceIn(MIN_SIZE, MAX_SIZE)) / STEP).roundToInt() * STEP
    }
}
