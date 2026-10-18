package okuudra.gui

import com.mojang.blaze3d.platform.InputConstants
import okuudra.api.hud.Hud
import okuudra.api.render.Canvas
import okuudra.api.render.Icons
import okuudra.api.render.UiFont
import okuudra.api.util.Argb
import okuudra.config.Category
import okuudra.config.ConfigStore
import okuudra.config.Feature
import okuudra.config.Features
import okuudra.features.impl.gui.Interface
import net.minecraft.client.Minecraft
import kotlin.math.cos

class ConfigScreen(initialTab: Int? = null) : UiScreen("okuudra") {
    private val hits = Hits()
    private val panel = SettingsPanel()
    private val search = TextField(128)
    private val listMotion = Motion()
    private val tabY = Motion()
    private val railMotion = Motion(if (Interface.sidebarPinned.value) 1f else 0f)
    private var railWidth = if (Interface.sidebarPinned.value) 184f else 60f
    private var navigationReady = false
    private var feature: Feature? = requestedFeature
    private var tab = initialTab ?: if (feature === Interface) 1 else 0
    private var changedAt = System.currentTimeMillis()
    private var listScroll = 0f
    private var listHeight = 0f
    private var px = 0f
    private var py = 0f
    private var pw = 0f
    private var ph = 0f
    private var listW = 0f

    override val coordinateScale: Float
        get() = minOf(Theme.uiScale * 0.65f / Minecraft.getInstance().window.guiScale.toFloat(), width / 868f, height / 588f).coerceAtLeast(0.01f)

    init {
        requestedFeature = null
        search.onChange = {
            listScroll = 0f
            listMotion.snap(0f)
            feature = null
            panel.reset()
        }
    }

    override fun draw(c: Canvas) {
        hits.begin()
        pw = minOf(820f, layoutWidth - 48f)
        ph = minOf(540f, layoutHeight - 48f)
        px = (layoutWidth - pw) / 2f
        py = (layoutHeight - ph) / 2f
        listW = 60f
        val entrance = ((System.currentTimeMillis() - openedAt) / Theme.animationMs).coerceIn(0f, 1f)
        val eased = 1f - (1f - entrance) * (1f - entrance) * (1f - entrance)
        c.rect(0f, 0f, layoutWidth, layoutHeight, Argb.scaleAlpha(Theme.SCRIM, eased))
        c.push()
        c.alpha(eased)
        c.shadow(px, py, pw, ph, Theme.RADIUS, SHADOW_BLUR, SHADOW_COLOR)
        c.rect(px, py, pw, ph, Theme.BACKGROUND, Theme.RADIUS)
        c.clip(px, py, pw, ph)
        header(c)
        val y = py + 64f
        val h = ph - 64f
        val near = inside(px - 8f, y, railWidth + 20f, h)
        val expansion = railMotion.toward(if (near || Interface.sidebarPinned.value) 1f else 0f).coerceIn(0f, 1f)
        railWidth = listW + 124f * expansion
        val x = px + railWidth
        val w = pw - railWidth
        val progress = ((System.currentTimeMillis() - changedAt) / Theme.animationMs).coerceIn(0f, 1f)
        c.push()
        c.alpha(progress)
        if (tab == 0) {
            val active = feature
            if (active != null) {
                val bx = x + 24f
                val by = y + 12f
                Icons.draw(c, "back", bx, by + 5f, 12f, Theme.TEXT_DIM)
                c.text(UiFont.TEXT_SEMIBOLD, "Features", bx + 22f, by + 5f, 12f, Theme.TEXT_DIM)
                hits.add(bx - 4f, by, 150f, 26f) {
                    feature = null
                    change()
                }
                panel.draw(c, active, x + 12f, y + 40f, w - 24f, h - 48f, hits, mouseX, mouseY)
            } else {
                featureList(c, x, y, w, h)
            }
        } else {
            panel.draw(
                c, Interface, x + 16f, y + 8f, w - 32f, h - 16f, hits, mouseX, mouseY,
                SettingsPanel.Mode.GENERAL,
                search.text,
            )
        }
        c.pop()
        navigation(c, y, h, expansion)
        c.unclip()
        c.border(px, py, pw, ph, Theme.BORDER, Theme.RADIUS)
        panel.drawPicker(c, hits, mouseX, mouseY)
        c.pop()
        hits.end()
    }

    private fun header(c: Canvas) {
        Icons.draw(c, "logo", px + 20f, py + 20f, 22f, Theme.accent)
        val phase = (System.nanoTime() % 4_000_000_000L) / 4_000_000_000f
        val prefixWidth = c.text(UiFont.DISPLAY_SEMIBOLD, "o", px + 51f, py + 24f, 16f, Argb.WHITE)
        val nameWidth = c.textWidth(UiFont.DISPLAY_SEMIBOLD, "Kuudra", 16f)
        c.text(UiFont.DISPLAY_SEMIBOLD, "Kuudra", px + 51f + prefixWidth, py + 24f, 16f, Argb.WHITE) { offset ->
            val blend = (0.5 - 0.5 * cos((offset / nameWidth - phase) * Math.PI * 2)).toFloat()
            Argb.mix(0xFF1E3F75.toInt(), 0xFF6A9BE6.toInt(), blend)
        }
        val sx = px + pw - 214f
        Widgets.input(c, sx, py + 17f, 194f, 30f, search.text, search.focused, "Search")
        hits.add(sx, py + 17f, 194f, 30f) { search.focused = true }
        c.hline(px, py + 63f, pw, Theme.BORDER)
    }

    private fun navigation(c: Canvas, y: Float, h: Float, expansion: Float) {
        c.rect(px, y, railWidth, h, Theme.RAIL)
        c.vline(px + railWidth, y, h, Theme.BORDER)
        c.clip(px, y, railWidth, h)
        hits.add(px, y, railWidth, h) { }
        val selectedY = y + 16f + tab * 44f
        if (!navigationReady) {
            tabY.snap(selectedY)
            navigationReady = true
        }
        c.rect(px + 10f, tabY.toward(selectedY), railWidth - 20f, 38f, Theme.accentSoft, Theme.RADIUS_SMALL)
        for ((index, title) in TABS.withIndex()) {
            val cy = y + 16f + index * 44f
            val selected = tab == index
            val over = inside(px + 10f, cy, railWidth - 20f, 38f)
            if (over && !selected) c.rect(px + 10f, cy, railWidth - 20f, 38f, Theme.ROW_HOVER, Theme.RADIUS_SMALL)
            val color = if (selected) Theme.TEXT else Theme.TEXT_DIM
            Icons.draw(c, ICONS[index], px + 20f, cy + 9f, 20f, if (selected) Theme.accent else color)
            c.text(UiFont.TEXT_SEMIBOLD, title, px + 60f - (1f - expansion) * 6f, cy + 13f, 12f, Argb.scaleAlpha(color, expansion))
            hits.add(px + 10f, cy, railWidth - 20f, 38f) {
                if (tab != index || feature != null) {
                    tab = index
                    search.clear()
                    feature = null
                    listScroll = 0f
                    listMotion.snap(0f)
                    change()
                }
            }
        }
        if (Hud.elements.isNotEmpty()) {
            val fy = y + h - 94f
            c.hline(px + 16f, fy, railWidth - 32f, Theme.BORDER)
            if (inside(px + 10f, fy + 8f, railWidth - 20f, 32f)) c.rect(px + 10f, fy + 8f, railWidth - 20f, 32f, Theme.ROW_HOVER, Theme.RADIUS_SMALL)
            Icons.draw(c, "hud", px + 20f, fy + 14f, 20f, Theme.TEXT_DIM)
            c.text(UiFont.TEXT, "HUD layout", px + 60f - (1f - expansion) * 6f, fy + 18f, 11f, Argb.scaleAlpha(Theme.TEXT_DIM, expansion))
            hits.add(px + 10f, fy + 8f, railWidth - 20f, 32f) { open(HudEditScreen()) }
        }
        val pinY = y + h - 42f
        val pinned = Interface.sidebarPinned.value
        if (pinned || inside(px + 10f, pinY, railWidth - 20f, 32f)) {
            c.rect(px + 10f, pinY, railWidth - 20f, 32f, if (pinned) Theme.accentSoft else Theme.ROW_HOVER, Theme.RADIUS_SMALL)
        }
        Icons.draw(c, "pin", px + 20f, pinY + 6f, 20f, if (pinned) Theme.accent else Theme.TEXT_DIM)
        c.text(
            UiFont.TEXT,
            if (pinned) "Unpin sidebar" else "Pin sidebar",
            px + 60f - (1f - expansion) * 6f,
            pinY + 10f,
            11f,
            Argb.scaleAlpha(Theme.TEXT_DIM, expansion),
        )
        hits.add(px + 10f, pinY, railWidth - 20f, 32f) { Interface.sidebarPinned.value = !Interface.sidebarPinned.value }
        c.unclip()
    }

    private fun featureList(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        c.text(UiFont.DISPLAY_SEMIBOLD, "Features", x + 32f, y + 28f, Theme.HEADER_SIZE, Theme.TEXT)
        val top = y + 64f
        val bottom = y + h - 16f
        val groups = Category.entries.mapNotNull { category ->
            val items = Features.all.filter { it.toggleable && it.category == category && matches(it) }
            if (items.isEmpty()) null else category to items
        }
        listHeight = groups.sumOf { (_, items) -> (52 + items.size * 44).toDouble() }.toFloat()
        listScroll = listScroll.coerceIn(0f, (listHeight - (bottom - top)).coerceAtLeast(0f))
        val scroll = listMotion.toward(listScroll)
        c.clip(x + 24f, top, w - 48f, bottom - top)
        var cy = top - scroll
        for ((category, items) in groups) {
            c.text(UiFont.MONO, category.label, x + 36f, cy + 10f, 15f, Theme.TEXT_DIM)
            val dividerX = x + 48f + c.textWidth(UiFont.MONO, category.label, 15f)
            c.hline(dividerX, cy + 18f, x + w - 36f - dividerX, Theme.BORDER)
            cy += 36f
            for (item in items) {
                val visibleTop = maxOf(cy, top)
                val visibleBottom = minOf(cy + 40f, bottom)
                val over = inside(x + 24f, visibleTop, w - 48f, (visibleBottom - visibleTop).coerceAtLeast(0f))
                if (over) c.rect(x + 24f, cy, w - 48f, 40f, Theme.ROW_HOVER, Theme.RADIUS_SMALL)
                val title = c.trim(UiFont.TEXT_SEMIBOLD, item.name, 15f, w - 170f)
                val titleWidth = c.text(UiFont.TEXT_SEMIBOLD, title, x + 36f, cy + 12f, 15f, Theme.TEXT)
                val lineX = x + 48f + titleWidth
                val lineEnd = x + w - if (item.toggleable) 108f else 60f
                c.hline(lineX, cy + 28f, (lineEnd - lineX).coerceAtLeast(0f), Argb.mix(Theme.BACKGROUND, Theme.BORDER, 0.6f))
                Icons.draw(c, "right", x + w - 48f, cy + 14f, 12f, Theme.TEXT_FAINT)
                if (visibleBottom > visibleTop) {
                    hits.add(x + 24f, visibleTop, w - 48f, visibleBottom - visibleTop) {
                        feature = item
                        change()
                    }
                    if (item.toggleable) {
                        val sx = x + w - 96f
                        val sy = cy + 12f
                        Widgets.switch(c, sx, sy, item.enabled, inside(sx, sy, Widgets.SWITCH_W, Widgets.SWITCH_H))
                        val switchTop = maxOf(sy - 4f, top)
                        val switchBottom = minOf(sy + Widgets.SWITCH_H + 4f, bottom)
                        if (switchBottom > switchTop) hits.add(sx - 4f, switchTop, Widgets.SWITCH_W + 8f, switchBottom - switchTop) { item.toggle() }
                    }
                }
                cy += 44f
            }
            cy += 16f
        }
        if (groups.isEmpty()) c.text(UiFont.TEXT, "No matching features", x + 36f, top + 16f, 12f, Theme.TEXT_DIM)
        c.unclip()
        Widgets.scrollbar(c, x + w - 8f, top, bottom - top, listHeight, scroll)
    }

    private fun change() {
        changedAt = System.currentTimeMillis()
        search.focused = false
        panel.reset()
    }

    private fun matches(item: Feature): Boolean = search.text.isBlank() ||
        (item.name + " " + item.description + " " + item.settings.joinToString { it.name }).contains(search.text, true)

    private fun inside(x: Float, y: Float, w: Float, h: Float): Boolean = Hits.inside(mouseX, mouseY, x, y, w, h)

    override fun onClick(x: Double, y: Double, button: Int): Boolean {
        if (System.currentTimeMillis() - openedAt < Theme.animationMs) return true
        search.focused = false
        panel.clickOutside(x, y)
        if (hits.click(x, y, button)) return true
        return true
    }

    override fun onRelease(x: Double, y: Double, button: Int): Boolean {
        panel.release()
        return true
    }

    override fun onDrag(x: Double, y: Double, button: Int, dx: Double, dy: Double): Boolean {
        if (panel.dragging) panel.dragTo(x)
        panel.picker?.drag(x, y)
        return true
    }

    override fun onScroll(x: Double, y: Double, dx: Double, dy: Double): Boolean {
        if (Hits.inside(x, y, px, py + 64f, railWidth, ph - 64f)) return true
        if (panel.scrollDropdown(x, y, -(dy * 30f).toFloat())) return true
        if (!Hits.inside(x, y, px, py + 64f, pw, ph - 64f)) return false
        if (x < px + listW) return true
        if (tab == 0 && feature == null) listScroll -= (dy * 32f).toFloat() else panel.scrollBy(-(dy * 32f).toFloat())
        return true
    }

    override fun onKey(key: Int, scancode: Int, modifiers: Int): Boolean {
        if (panel.key(key, modifiers) || search.key(key, modifiers)) return true
        if (key == InputConstants.KEY_ESCAPE && panel.picker != null) {
            panel.closePicker()
            return true
        }
        return false
    }

    override fun onChar(char: Char): Boolean = search.char(char) || panel.char(char)

    override fun tick() {
        super.tick()
        textInput(search.focused || panel.typing)
    }

    override fun removed() {
        panel.reset()
        ConfigStore.save()
        super.removed()
    }

    companion object {
        const val SHADOW_BLUR = 32f
        const val SHADOW_COLOR = 0x80000000.toInt()
        private val TABS = listOf("Features", "Settings")
        private val ICONS = listOf("modules", "settings")
        private var requestedFeature: Feature? = null
        fun show(feature: Feature) {
            requestedFeature = feature
        }
    }
}
