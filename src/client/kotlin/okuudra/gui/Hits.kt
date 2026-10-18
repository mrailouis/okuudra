package okuudra.gui

import com.mojang.blaze3d.platform.InputConstants

class Hits {
    private class Hit(val x: Float, val y: Float, val w: Float, val h: Float, val button: Int, val action: () -> Unit)

    private val hits = ArrayList<Hit>()
    private val next = ArrayList<Hit>()

    fun begin() {
        next.clear()
    }

    fun end() {
        hits.clear()
        hits.addAll(next)
    }

    fun add(x: Float, y: Float, w: Float, h: Float, button: Int = InputConstants.MOUSE_BUTTON_LEFT, action: () -> Unit) {
        next.add(Hit(x, y, w, h, button, action))
    }

    fun click(mx: Double, my: Double, button: Int): Boolean {
        for (hit in hits.asReversed()) {
            if (hit.button == button && mx >= hit.x && my >= hit.y && mx < hit.x + hit.w && my < hit.y + hit.h) {
                hit.action()
                return true
            }
        }
        return false
    }

    companion object {
        fun inside(mx: Double, my: Double, x: Float, y: Float, w: Float, h: Float): Boolean =
            mx >= x && my >= y && mx < x + w && my < y + h
    }
}
