package okuudra.gui

import com.mojang.blaze3d.platform.InputConstants
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.render.UiFont
import okuudra.api.util.Argb
import okuudra.config.ConfigStore
import kotlin.math.abs

class HudEditScreen : UiScreen("HUD") {
    private var dragging: HudElement? = null
    private var dragDx = 0f
    private var dragDy = 0f
    private var hovered: HudElement? = null

    override fun init() {
        Hud.editing = true
    }

    override fun draw(c: Canvas) {
        c.rect(0f, 0f, c.width, c.height, 0x66000000)
        c.vline(c.width / 2f, 0f, c.height, Argb.withAlpha(Theme.TEXT_FAINT, 60))
        c.hline(0f, c.height / 2f, c.width, Argb.withAlpha(Theme.TEXT_FAINT, 60))
        Hud.render(c, true)
        hovered = null
        for (element in Hud.elements) {
            val w = element.width * element.scale
            val h = element.height * element.scale
            val over = Hits.inside(mouseX, mouseY, element.x - 2f, element.y - 2f, w + 4f, h + 4f)
            if (over) hovered = element
            val color = if (element === dragging || over) Theme.accent else Argb.withAlpha(Theme.TEXT_DIM, 140)
            c.border(element.x - 2f, element.y - 2f, w + 4f, h + 4f, color, Theme.RADIUS_SMALL)
            if (over) c.text(UiFont.TEXT, "${element.id}  ${"%.2f".format(element.scale)}x", element.x, element.y - 11f, Theme.SMALL_SIZE, Theme.TEXT)
        }
        val help = "drag to move · scroll to scale · right click to reset scale · r to reset all · esc to close"
        c.textCentered(UiFont.TEXT, help, c.width / 2f, c.height - 16f, Theme.SMALL_SIZE, Theme.TEXT_DIM)
    }

    override fun onClick(x: Double, y: Double, button: Int): Boolean {
        val target = hovered ?: return false
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            target.scale = 1f
            return true
        }
        dragging = target
        dragDx = (x - target.x).toFloat()
        dragDy = (y - target.y).toFloat()
        return true
    }

    override fun onDrag(x: Double, y: Double, button: Int, dx: Double, dy: Double): Boolean {
        val target = dragging ?: return false
        var nx = (x - dragDx).toFloat().coerceIn(0f, width - target.width * target.scale)
        var ny = (y - dragDy).toFloat().coerceIn(0f, height - target.height * target.scale)
        val w = target.width * target.scale
        val h = target.height * target.scale
        if (abs(nx + w / 2f - width / 2f) < SNAP) nx = width / 2f - w / 2f
        if (abs(ny + h / 2f - height / 2f) < SNAP) ny = height / 2f - h / 2f
        if (nx < SNAP) nx = 0f
        if (ny < SNAP) ny = 0f
        if (width - (nx + w) < SNAP) nx = width - w
        if (height - (ny + h) < SNAP) ny = height - h
        target.x = nx
        target.y = ny
        return true
    }

    override fun onRelease(x: Double, y: Double, button: Int): Boolean {
        dragging = null
        return true
    }

    override fun onScroll(x: Double, y: Double, dx: Double, dy: Double): Boolean {
        val target = hovered ?: return false
        target.scale = (target.scale + dy.toFloat() * 0.1f).coerceIn(0.5f, 3f)
        return true
    }

    override fun onKey(key: Int, scancode: Int, modifiers: Int): Boolean {
        if (key == InputConstants.KEY_R) {
            Hud.elements.forEach { it.resetPosition() }
            return true
        }
        if (key == InputConstants.KEY_ESCAPE) {
            open(ConfigScreen())
            return true
        }
        return false
    }

    override fun removed() {
        Hud.editing = false
        ConfigStore.markDirty()
        ConfigStore.save()
        super.removed()
    }

    companion object {
        private const val SNAP = 6f
    }
}
