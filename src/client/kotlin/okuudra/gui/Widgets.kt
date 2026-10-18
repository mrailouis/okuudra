package okuudra.gui

import okuudra.api.render.Canvas
import okuudra.api.render.UiFont
import okuudra.api.util.Argb

object Widgets {
    const val SWITCH_W = 28f
    const val SWITCH_H = 16f

    fun switch(c: Canvas, x: Float, y: Float, on: Boolean, hovered: Boolean, progress: Float = if (on) 1f else 0f) {
        val t = progress.coerceIn(0f, 1f)
        c.rect(x, y, SWITCH_W, SWITCH_H, Argb.mix(if (hovered) Theme.BORDER_STRONG else Theme.ELEMENT, Theme.accent, t), 8f)
        c.circle(x + 8f + 12f * t, y + 8f, 5f, Argb.mix(Theme.TEXT_DIM, Theme.BACKGROUND, t))
    }

    fun button(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, hovered: Boolean, accent: Boolean = false, danger: Boolean = false) {
        val fill = when {
            danger -> Argb.withAlpha(Theme.DANGER, if (hovered) 40 else 22)
            accent -> if (hovered) Argb.mix(Theme.accent, Theme.TEXT, 0.12f) else Theme.accent
            hovered -> Theme.ROW_ACTIVE
            else -> Theme.ELEMENT
        }
        c.rect(x, y, w, h, fill, Theme.RADIUS_SMALL)
        val color = when {
            danger -> Theme.DANGER
            accent -> Theme.BACKGROUND
            else -> Theme.TEXT
        }
        val size = Theme.SMALL_SIZE
        c.textCentered(UiFont.TEXT_SEMIBOLD, c.trim(UiFont.TEXT_SEMIBOLD, label, size, w - 6f), x + w / 2f, y + (h - size) / 2f - 0.5f, size, color)
    }

    fun slider(c: Canvas, x: Float, y: Float, w: Float, fraction: Float, hovered: Boolean) {
        val h = 4f
        c.rect(x, y, w, h, Theme.ELEMENT, 2f)
        val fill = (w * fraction.coerceIn(0f, 1f)).coerceAtLeast(0f)
        if (fill > 0f) c.rect(x, y, fill, h, Theme.SLIDER, 2f)
        c.circle(x + fill, y + h / 2f, if (hovered) 5f else 4.5f, Theme.TEXT)
    }

    fun input(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, focused: Boolean, placeholder: String = "") {
        c.rect(x, y, w, h, Theme.FIELD, Theme.RADIUS_SMALL)
        if (focused) c.border(x, y, w, h, Theme.accent, Theme.RADIUS_SMALL)
        val size = Theme.SMALL_SIZE
        val ty = y + (h - size) / 2f - 0.5f
        c.clip(x + 8f, y, w - 16f, h)
        if (text.isEmpty() && !focused) {
            c.text(UiFont.TEXT, placeholder, x + 10f, ty, size, Theme.TEXT_FAINT)
        } else {
            val shown = if (c.textWidth(UiFont.TEXT, text, size) > w - 20f) tail(c, text, size, w - 20f) else text
            val tw = c.text(UiFont.TEXT, shown, x + 10f, ty, size, Theme.TEXT)
            if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) c.rect(x + 10f + tw + 1f, ty - 1f, 1f, size + 2f, Theme.TEXT)
        }
        c.unclip()
    }

    private fun tail(c: Canvas, text: String, size: Float, max: Float): String {
        var start = 0
        while (start < text.length && c.textWidth(UiFont.TEXT, text.substring(start), size) > max) start++
        return text.substring(start)
    }

    fun swatch(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, hovered: Boolean) {
        c.clip(x, y, w, h)
        checker(c, x, y, w, h)
        c.unclip()
        c.rect(x, y, w, h, color, Theme.RADIUS_SMALL)
        c.border(x, y, w, h, if (hovered) Theme.TEXT_DIM else Theme.BORDER_STRONG, Theme.RADIUS_SMALL)
    }

    fun checker(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        c.rect(x, y, w, h, 0xFF8A8A8A.toInt(), Theme.RADIUS_SMALL)
        val inset = minOf(2f, h / 4f)
        c.rect(x + inset, y + inset, w / 2f - inset, h / 2f - inset, 0xFFC8C8C8.toInt())
        c.rect(x + w / 2f, y + h / 2f, w / 2f - inset, h / 2f - inset, 0xFFC8C8C8.toInt())
    }

    fun scrollbar(c: Canvas, x: Float, y: Float, h: Float, content: Float, scroll: Float) {
        if (content <= h) return
        val thumb = (h * h / content).coerceAtLeast(12f)
        val ty = y + (h - thumb) * (scroll / (content - h)).coerceIn(0f, 1f)
        c.rect(x, ty, 2f, thumb, Argb.withAlpha(Theme.TEXT_DIM, 120), 1f)
    }
}
