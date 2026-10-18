package okuudra.gui

import com.mojang.blaze3d.platform.InputConstants
import okuudra.api.render.Canvas
import okuudra.api.render.UiFont
import okuudra.api.util.Argb
import okuudra.config.ColorSetting

class ColorPicker(private val setting: ColorSetting, private var x: Float, private var y: Float, screenW: Float, screenH: Float) {
    private val w = 220f
    private val h = 260f
    private val opened = System.nanoTime()
    private val hsv = Argb.toHsv(setting.value)
    private var alpha = Argb.alpha(setting.value)
    private var hex = Argb.hex(setting.value)
    private var hexFocused = false
    private var drag = 0

    init {
        if (x + w > screenW) x = screenW - w - 4f
        if (y + h > screenH) y = screenH - h - 4f
        if (x < 0f) x = 4f
        if (y < 0f) y = 4f
    }

    fun contains(mx: Double, my: Double): Boolean = Hits.inside(mx, my, x, y, w, h)

    fun draw(c: Canvas, hits: Hits, mx: Double = -1.0, my: Double = -1.0, label: String = "") {
        hits.add(x, y, w, h) {}
        c.push()
        c.alpha(((System.nanoTime() - opened) / 1e6 / Theme.animationMs).toFloat().coerceIn(0f, 1f))
        c.shadow(x, y, w, h, Theme.RADIUS, 16f, 0x60000000)
        c.rect(x, y, w, h, Theme.PANEL, Theme.RADIUS)
        c.border(x, y, w, h, Theme.BORDER, Theme.RADIUS)
        c.text(UiFont.TEXT_SEMIBOLD, c.trim(UiFont.TEXT_SEMIBOLD, label, Theme.SMALL_SIZE, w - 24f), x + 12f, y + 12f, Theme.SMALL_SIZE, Theme.TEXT)
        val pad = 12f
        val sq = w - pad * 2f
        val sqH = 140f
        val sx = x + pad
        val sy = y + pad + 24f
        val hue = Argb.fromHsv(hsv[0], 1f, 1f)
        c.gradient4(sx, sy, sq, sqH, Argb.WHITE, hue, Argb.BLACK, Argb.BLACK, Theme.RADIUS_SMALL)
        val px = sx + hsv[1] * sq
        val py = sy + (1f - hsv[2]) * sqH
        c.ring(px, py, 3.5f, Argb.WHITE, 1.5f)
        hits.add(sx, sy, sq, sqH) {
            drag = 1
            drag(mx, my)
        }

        val hy = sy + sqH + 12f
        val seg = sq / 6f
        for (i in 0 until 6) {
            val a = Argb.fromHsv(i * 60f, 1f, 1f)
            val b = Argb.fromHsv((i + 1) * 60f, 1f, 1f)
            c.gradient4(sx + seg * i, hy, seg, 10f, a, b, b, a)
        }
        val hx = sx + (hsv[0] / 360f) * sq
        c.rect(hx - 1f, hy - 1f, 2f, 12f, Argb.WHITE, 1f)
        hits.add(sx, hy, sq, 10f) {
            drag = 2
            drag(mx, my)
        }

        val ay = hy + 22f
        Widgets.checker(c, sx, ay, sq, 10f)
        c.gradient4(sx, ay, sq, 10f, Argb.withAlpha(rgb(), 0), Argb.withAlpha(rgb(), 255), Argb.withAlpha(rgb(), 255), Argb.withAlpha(rgb(), 0))
        val ax = sx + (alpha / 255f) * sq
        c.rect(ax - 1f, ay - 1f, 2f, 12f, Argb.WHITE, 1f)
        hits.add(sx, ay, sq, 10f) {
            drag = 3
            drag(mx, my)
        }

        val fy = ay + 22f
        Widgets.swatch(c, sx, fy, 28f, 28f, setting.value, false)
        Widgets.input(c, sx + 36f, fy, sq - 36f, 28f, hex, hexFocused)
        hits.add(sx + 36f, fy, sq - 36f, 28f) { hexFocused = true }
        c.pop()
    }

    fun drag(mx: Double, my: Double) {
        val pad = 12f
        val sq = w - pad * 2f
        val sx = x + pad
        val sy = y + pad + 24f
        when (drag) {
            1 -> {
                hsv[1] = ((mx - sx) / sq).toFloat().coerceIn(0f, 1f)
                hsv[2] = (1.0 - (my - sy) / 140.0).toFloat().coerceIn(0f, 1f)
                apply()
            }
            2 -> {
                hsv[0] = ((mx - sx) / sq * 360.0).toFloat().coerceIn(0f, 359.99f)
                apply()
            }
            3 -> {
                alpha = ((mx - sx) / sq * 255.0).toInt().coerceIn(0, 255)
                apply()
            }
        }
    }

    fun release() {
        drag = 0
    }

    fun key(key: Int): Boolean {
        if (!hexFocused) return false
        when (key) {
            InputConstants.KEY_BACKSPACE -> hex = hex.dropLast(1)
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                Argb.parse(hex)?.let {
                    setting.value = it
                    Argb.toHsv(it).copyInto(hsv)
                    alpha = Argb.alpha(it)
                }
                hexFocused = false
            }
            InputConstants.KEY_ESCAPE -> hexFocused = false
            else -> return false
        }
        return true
    }

    fun char(ch: Char): Boolean {
        if (!hexFocused) return false
        if (hex.length < 9 && (ch.isLetterOrDigit() || ch == '#')) hex += ch.uppercaseChar()
        return true
    }

    fun unfocus() {
        hexFocused = false
    }

    val focused: Boolean get() = hexFocused

    private fun rgb(): Int = Argb.fromHsv(hsv[0], hsv[1], hsv[2])

    private fun apply() {
        setting.value = Argb.withAlpha(rgb(), alpha)
        hex = Argb.hex(setting.value)
    }
}
