package okuudra.api.hud

import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier
import okuudra.OKuudra
import okuudra.api.render.Canvas
import okuudra.api.render.UiFrame
import okuudra.api.util.Argb
import okuudra.config.ConfigStore

abstract class HudElement(val id: String, private var defaultX: Float, private var defaultY: Float, private var defaultScale: Float = 1f) {
    var x = defaultX
    var y = defaultY
    var scale = defaultScale
    var width = 0f
        protected set
    var height = 0f
        protected set

    abstract fun visible(): Boolean

    abstract fun render(c: Canvas, editing: Boolean)

    fun adoptDefault() {
        defaultX = x
        defaultY = y
        defaultScale = scale
    }

    fun resetPosition() {
        x = defaultX
        y = defaultY
        scale = defaultScale
    }

    protected fun size(w: Float, h: Float) {
        width = w
        height = h
    }

    protected fun label(c: Canvas, label: String, value: String = "") {
        val w = c.mcText("§l$label", 0f, 0f, Hud.LABEL)
        val v = if (value.isEmpty()) 0f else c.mcText(value, w, 0f, Argb.WHITE)
        size(w + v, c.mcLineHeight)
    }

    protected fun lines(c: Canvas, vararg lines: String, color: Int = -1) {
        var w = 0f
        var y = 0f
        for (line in lines) {
            w = maxOf(w, c.mcText(line, 0f, y, color))
            y += c.mcLineHeight + 1f
        }
        size(w, y)
    }
}

object Hud {
    const val LABEL = 0xFF3260A8.toInt()
    private val registered = ArrayList<HudElement>()
    var editing = false

    val elements: List<HudElement> get() = registered

    fun register(vararg elements: HudElement) {
        registered.addAll(elements)
    }

    fun init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OKuudra.MOD_ID, "hud")) { extractor, _ ->
            if (!editing) UiFrame.draw(extractor) { render(it, false) }
        }
        ConfigStore.section("hud", ::write, ::read)
    }

    fun render(c: Canvas, editing: Boolean) {
        for (element in registered) {
            if (!editing && !element.visible()) continue
            c.push()
            c.translate(element.x, element.y)
            c.scale(element.scale)
            element.render(c, editing)
            c.pop()
        }
    }

    private fun write(): JsonObject {
        val obj = JsonObject()
        for (element in registered) {
            val e = JsonObject()
            e.addProperty("x", element.x)
            e.addProperty("y", element.y)
            e.addProperty("scale", element.scale)
            obj.add(element.id, e)
        }
        return obj
    }

    private fun read(obj: JsonObject) {
        for (element in registered) {
            val e = obj.getAsJsonObject(element.id) ?: continue
            e.get("x")?.let { element.x = it.asFloat }
            e.get("y")?.let { element.y = it.asFloat }
            e.get("scale")?.let { element.scale = it.asFloat }
        }
    }
}
