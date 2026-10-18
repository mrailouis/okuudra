package okuudra.gui

import com.mojang.blaze3d.platform.InputConstants
import okuudra.api.input.Keys
import okuudra.api.render.Canvas
import okuudra.api.render.Icons
import okuudra.api.render.UiFont
import okuudra.config.ActionSetting
import okuudra.config.ChoiceSetting
import okuudra.config.ColorSetting
import okuudra.config.Feature
import okuudra.config.Features
import okuudra.config.InfoSetting
import okuudra.config.KeybindSetting
import okuudra.config.ListSetting
import okuudra.config.Setting
import okuudra.config.SliderSetting
import okuudra.config.TextSetting
import okuudra.config.ToggleSetting

class SettingsPanel {
    enum class Mode { ALL, COLORS, KEYBINDS, GENERAL }

    private val motions = HashMap<Any, Motion>()
    private val scrollMotion = Motion()
    private var clipTop = 0f
    private var clipBottom = 0f
    private var query = ""
    private var labelWidth = 0f
    var scroll = 0f
    var contentHeight = 0f
        private set
    private var dragSlider: SliderSetting? = null
    private var dragX = 0f
    private var dragW = 0f
    private val textField = TextField(1024)
    private var focusedText: TextSetting? = null
    var capturing: Any? = null
        private set
    var picker: ColorPicker? = null
        private set
    private var pickerLabel = ""
    private var openList: Setting<String>? = null
    private var dropdownX = 0f
    private var dropdownY = 0f
    private var dropdownW = 170f
    private var dropdownAt = 0L
    private var dropdownHeight = 0f
    private var dropdownScroll = 0f
    private var dropdownContent = 0f
    private var dropdownDrawX = 0f
    private var dropdownDrawY = 0f
    private val captureTargets = HashMap<Any, (Int) -> Unit>()
    private var hoverSetting: Setting<*>? = null
    private var hoverSince = 0L
    private var tooltip: String? = null
    private var tooltipX = 0f
    private var tooltipY = 0f

    fun draw(c: Canvas, feature: Feature, x: Float, y: Float, w: Float, h: Float, hits: Hits, mx: Double, my: Double, mode: Mode = Mode.ALL, search: String = "") {
        clipTop = y
        clipBottom = y + h
        query = search
        c.clip(x, y, w, h)
        tooltip = null
        var cy = y + Theme.PAD
        c.text(
            UiFont.DISPLAY_SEMIBOLD,
            when (mode) {
                Mode.COLORS -> "Colors"
                Mode.KEYBINDS -> "Keybinds"
                Mode.GENERAL -> "Interface"
                else -> feature.name
            },
            x + Theme.PAD,
            cy,
            Theme.HEADER_SIZE,
            Theme.TEXT,
        )
        if (mode == Mode.ALL && feature.toggleable) {
            val sx = x + w - Theme.PAD - 4f - Widgets.SWITCH_W
            Widgets.switch(
                c,
                sx,
                cy,
                feature.enabled,
                Hits.inside(mx, my, sx, cy, Widgets.SWITCH_W, Widgets.SWITCH_H),
                motions.getOrPut(feature) {
                    Motion(if (feature.enabled) 1f else 0f)
                }.toward(if (feature.enabled) 1f else 0f, 100f),
            )
            addHit(hits, sx - 4f, cy - 4f, Widgets.SWITCH_W + 8f, Widgets.SWITCH_H + 8f) { feature.toggle() }
        }
        cy += Theme.HEADER_SIZE + 8f
        if (mode == Mode.ALL && feature.description.isNotEmpty()) {
            for (line in c.wrap(UiFont.TEXT, feature.description, Theme.SMALL_SIZE, w - Theme.PAD * 2f - 24f)) {
                c.text(UiFont.TEXT, line, x + Theme.PAD, cy, Theme.SMALL_SIZE, Theme.TEXT_DIM)
                cy += Theme.SMALL_SIZE + 2f
            }
        }
        cy += 4f
        cy += 6f
        c.unclip()
        clipTop = cy
        val viewport = (clipBottom - clipTop).coerceAtLeast(1f)
        scroll = scroll.coerceIn(0f, (contentHeight - viewport).coerceAtLeast(0f))
        val shownScroll = scrollMotion.toward(scroll)
        c.clip(x, clipTop, w, viewport)
        cy -= shownScroll
        val rowsStart = cy
        cy = rows(c, feature, x, cy, w, hits, mx, my, mode)

        if (cy == rowsStart) {
            c.text(UiFont.TEXT, "No matching settings", x + Theme.PAD, cy + 18f, Theme.BODY_SIZE, Theme.TEXT_DIM)
            cy += Theme.ROW_HEIGHT
        }
        contentHeight = cy + shownScroll - clipTop + Theme.PAD
        c.unclip()
        Widgets.scrollbar(c, x + w - 3f, clipTop + 2f, viewport - 4f, contentHeight, shownScroll)
        scroll = scroll.coerceIn(0f, (contentHeight - viewport).coerceAtLeast(0f))
    }

    private fun rows(c: Canvas, feature: Feature, x: Float, y: Float, w: Float, hits: Hits, mx: Double, my: Double, mode: Mode): Float {
        var cy = y
        var hovering: Setting<*>? = null
        val owners = if (mode == Mode.COLORS || mode == Mode.KEYBINDS) Features.all else listOf(feature)
        for (owner in owners) {
            val settings = owner.settings.filter {
                it.visible() && accepts(mode, it) &&
                    (query.isBlank() || owner.name.contains(query, true) || it.title.contains(query, true) || it.description.contains(query, true))
            }
            val toggleKey = mode == Mode.KEYBINDS && owner.toggleable && (query.isBlank() || owner.name.contains(query, true))
            if (settings.isEmpty() && !toggleKey) continue
            val groups = if (mode == Mode.ALL || mode == Mode.GENERAL) settings.groupBy { it.section } else linkedMapOf(owner.name to settings)
            for ((title, rows) in groups) {
                if (title.isNotEmpty()) cy = sectionHeading(c, title, x, cy, w)
                if (toggleKey) {
                    cy =
                        keybindRow(c, "Toggle ${owner.name}", owner.keybind, owner, "Turns ${owner.name} on or off.", x, cy, w, hits, mx, my) { owner.keybind = it }
                }
                for (setting in rows) {
                    val top = cy
                    cy = row(c, setting, x, cy, w, hits, mx, my)
                    if (setting.description.isNotEmpty() && Hits.inside(mx, my, x, top, w, cy - top) && Hits.inside(mx, my, x, clipTop, w, clipBottom - clipTop)) {
                        hovering = setting
                        if (setting !== hoverSetting) {
                            hoverSetting = setting
                            hoverSince = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - hoverSince > 400L) {
                            tooltip = setting.description
                            tooltipX = x + Theme.PAD
                            tooltipY = cy
                        }
                    }
                }
                cy += 8f
            }
        }
        if (mode == Mode.ALL && feature.toggleable) {
            cy = sectionHeading(c, "Keybind", x, cy, w)
            cy =
                keybindRow(c, "Toggle key", feature.keybind, feature, "Turns ${feature.name} on or off. Right-click to clear.", x, cy, w, hits, mx, my) {
                    feature.keybind =
                        it
                }
        }
        if (hovering == null) hoverSetting = null

        return cy
    }

    private fun sectionHeading(c: Canvas, title: String, x: Float, y: Float, w: Float): Float {
        c.text(UiFont.TEXT_SEMIBOLD, title, x + Theme.PAD, y + 16f, Theme.SMALL_SIZE, Theme.TEXT_FAINT)
        val start = x + Theme.PAD + c.textWidth(UiFont.TEXT_SEMIBOLD, title, Theme.SMALL_SIZE) + 12f
        c.hline(start, y + 22f, (x + w - Theme.PAD - start).coerceAtLeast(0f), Theme.BORDER)
        return y + 36f
    }

    private fun accepts(mode: Mode, setting: Setting<*>): Boolean = when (mode) {
        Mode.ALL -> true
        Mode.COLORS -> setting is ColorSetting
        Mode.KEYBINDS -> setting is KeybindSetting
        Mode.GENERAL -> setting !is ColorSetting
    }

    private fun addHit(hits: Hits, x: Float, y: Float, w: Float, h: Float, button: Int = InputConstants.MOUSE_BUTTON_LEFT, action: () -> Unit) {
        val top = y.coerceAtLeast(clipTop)
        val bottom = (y + h).coerceAtMost(clipBottom)
        if (bottom > top) hits.add(x, top, w, bottom - top, button, action)
    }

    private fun row(c: Canvas, setting: Setting<*>, x: Float, y: Float, w: Float, hits: Hits, mx: Double, my: Double): Float {
        val left = x + Theme.PAD
        val right = x + w - Theme.PAD - 4f
        labelWidth = (
            w - Theme.PAD * 2f - when (setting) {
                is TextSetting -> minOf(210f, w * 0.45f) + 12f
                is ChoiceSetting, is ListSetting, is KeybindSetting -> 180f
                is ColorSetting -> 115f
                is ActionSetting -> 115f
                else -> 36f
            }
            ).coerceAtLeast(20f)
        val rowH = Theme.ROW_HEIGHT
        if (setting !is InfoSetting && setting !is KeybindSetting) {
            if (Hits.inside(mx, my, x, y, w, rowH)) c.rect(x + 4f, y + 2f, w - 8f, rowH - 4f, Theme.PANEL, Theme.RADIUS_SMALL)
        }
        val hovered = Hits.inside(mx, my, x, y, w, rowH)
        val labelY = y + (Theme.ROW_HEIGHT - Theme.BODY_SIZE) / 2f - 0.5f
        when (setting) {
            is ToggleSetting -> {
                label(c, setting, left, labelY, hovered)
                val sx = right - Widgets.SWITCH_W
                val sy = y + (Theme.ROW_HEIGHT - Widgets.SWITCH_H) / 2f
                Widgets.switch(
                    c,
                    sx,
                    sy,
                    setting.value,
                    hovered,
                    motions.getOrPut(setting) {
                        Motion(if (setting.value) 1f else 0f)
                    }.toward(if (setting.value) 1f else 0f, 100f),
                )
                addHit(hits, x, y, w, rowH) { setting.value = !setting.value }
            }
            is SliderSetting -> {
                val controlW = minOf(190f, w * 0.45f)
                labelWidth = (w - Theme.PAD * 2f - controlW - 16f).coerceAtLeast(20f)
                label(c, setting, left, labelY, hovered)
                c.textRight(UiFont.TEXT, setting.display(), right, labelY, Theme.SMALL_SIZE, Theme.TEXT_DIM)
                val tx = right - controlW
                val tw = controlW - 52f
                val ty = y + rowH / 2f - 2f
                Widgets.slider(c, tx, ty, tw, (setting.value - setting.min) / (setting.max - setting.min), hovered)
                addHit(hits, tx - 4f, ty - 8f, tw + 8f, 20f) {
                    dragSlider = setting
                    dragX = tx
                    dragW = tw
                    dragTo(mx)
                }
            }
            is ChoiceSetting -> dropdownRow(c, setting, x, y, w, hits, mx, my)
            is ListSetting -> dropdownRow(c, setting, x, y, w, hits, mx, my)

            is ColorSetting -> {
                label(c, setting, left, labelY, hovered)
                val sw = 20f
                val sx = right - sw
                val sy = y + 12f
                c.textRight(UiFont.TEXT, "#%06X".format(setting.value and 0xFFFFFF), sx - 7f, labelY, 11f, Theme.TEXT_DIM)
                Widgets.swatch(c, sx, sy, sw, sw, setting.value, Hits.inside(mx, my, sx, sy, sw, sw))
                addHit(hits, sx, sy, sw, sw) {
                    picker = ColorPicker(setting, sx - 100f, sy + 32f, c.layoutWidth, c.layoutHeight)
                    pickerLabel = setting.name
                }
            }
            is KeybindSetting -> return keybindRow(c, setting.title, setting.value, setting, setting.description, x, y, w, hits, mx, my) { setting.value = it }
            is TextSetting -> {
                label(c, setting, left, labelY, hovered)
                val iw = minOf(210f, w * 0.45f)
                val ix = right - iw
                val iy = y + 8f
                val focused = focusedText === setting
                Widgets.input(c, ix, iy, iw, 28f, if (focused) textField.text else setting.value, focused)
                addHit(hits, ix, iy, iw, 28f) { focus(setting) }
            }
            is ActionSetting -> {
                label(c, setting, left, labelY, hovered)
                Widgets.button(
                    c,
                    right - 100f,
                    y + 8f,
                    100f,
                    28f,
                    setting.label,
                    Hits.inside(mx, my, right - 100f, y + 8f, 100f, 28f),
                    danger = setting.danger,
                )
                addHit(hits, right - 100f, y + 8f, 100f, 28f) { setting.action() }
            }

            is InfoSetting -> return infoRow(c, setting, left, y, w)
        }
        return y + rowH
    }

    private fun infoRow(c: Canvas, setting: InfoSetting, left: Float, y: Float, w: Float): Float {
        var iy = y + 8f
        if (setting.name.isNotEmpty()) {
            c.text(UiFont.TEXT_SEMIBOLD, setting.name, left, iy, Theme.BODY_SIZE, Theme.TEXT)
            iy += Theme.BODY_SIZE + 3f
        }
        for (line in setting.lines) {
            for (part in c.wrap(UiFont.TEXT, line, Theme.SMALL_SIZE, w - Theme.PAD * 2f - 4f)) {
                c.text(UiFont.TEXT, part, left, iy, Theme.SMALL_SIZE, Theme.TEXT_DIM)
                iy += Theme.SMALL_SIZE + 2f
            }
        }
        return iy + 12f
    }

    private fun dropdownRow(c: Canvas, setting: Setting<String>, x: Float, y: Float, w: Float, hits: Hits, mx: Double, my: Double) {
        label(c, setting, x + Theme.PAD, y + (Theme.ROW_HEIGHT - Theme.BODY_SIZE) / 2f, Hits.inside(mx, my, x, y, w, Theme.ROW_HEIGHT))
        val bx = x + w - Theme.PAD - 174f
        val by = y + 8f
        Widgets.input(c, bx, by, 170f, 28f, c.trim(UiFont.TEXT, setting.value, Theme.SMALL_SIZE, 130f), false)
        if (openList === setting) c.border(bx, by, 170f, 28f, Theme.accent, Theme.RADIUS_SMALL)
        Icons.draw(c, if (openList === setting) "up" else "down", bx + 150f, by + 8f, 12f, Theme.TEXT_DIM)
        if (openList === setting) {
            dropdownX = bx
            dropdownY = by + 32f
        }
        addHit(hits, bx, by, 170f, 28f) {
            openList = if (openList === setting) null else setting
            dropdownX = bx
            dropdownY = by + 32f
            dropdownW = 170f
            dropdownAt = System.nanoTime()
            dropdownScroll = 0f
        }
    }

    private fun dropdown(c: Canvas, setting: Setting<String>, hits: Hits, mx: Double, my: Double) {
        val options = when (setting) {
            is ChoiceSetting -> setting.options
            is ListSetting -> setting.options()
            else -> return
        }
        dropdownContent = options.size * 30f + 8f
        val h = minOf(dropdownContent, 308f, c.layoutHeight - 8f).coerceAtLeast(8f)
        dropdownScroll = dropdownScroll.coerceIn(0f, (dropdownContent - h).coerceAtLeast(0f))
        val x = dropdownX.coerceIn(4f, (c.layoutWidth - dropdownW - 4f).coerceAtLeast(4f))
        val y = dropdownY.coerceIn(4f, (c.layoutHeight - h - 4f).coerceAtLeast(4f))
        dropdownHeight = h
        dropdownDrawX = x
        dropdownDrawY = y
        val progress = ((System.nanoTime() - dropdownAt) / 1e6 / Theme.animationMs).toFloat().coerceIn(0f, 1f)
        hits.add(x, y, dropdownW, minOf(h, c.layoutHeight - y)) {}
        c.push()
        c.alpha(progress)
        c.rect(x, y, dropdownW, h, Theme.FIELD, Theme.RADIUS_SMALL)
        c.border(x, y, dropdownW, h, Theme.BORDER, Theme.RADIUS_SMALL)
        c.clip(x, y, dropdownW, minOf(h, c.layoutHeight - y))
        for ((index, option) in options.withIndex()) {
            val oy = y + 4f + index * 30f - dropdownScroll
            val active = setting.value == option
            if (active || Hits.inside(mx, my, x, oy, dropdownW, 30f)) c.rect(x + 4f, oy, dropdownW - 8f, 30f, if (active) Theme.accentSoft else Theme.ROW_HOVER, 5f)
            c.text(UiFont.TEXT, c.trim(UiFont.TEXT, option, 12f, dropdownW - 16f), x + 8f, oy + 9f, 12f, if (active) Theme.accent else Theme.TEXT)
            val top = maxOf(oy, y)
            val bottom = minOf(oy + 30f, y + h)
            if (bottom > top) {
                hits.add(x, top, dropdownW, bottom - top) {
                    setting.value = option
                    openList = null
                }
            }
        }
        c.unclip()
        Widgets.scrollbar(c, x + dropdownW - 3f, y + 4f, h - 8f, dropdownContent - 8f, dropdownScroll)
        c.pop()
    }

    private fun keybindRow(
        c: Canvas,
        name: String,
        key: Int,
        owner: Any,
        description: String,
        x: Float,
        y: Float,
        w: Float,
        hits: Hits,
        mx: Double,
        my: Double,
        set: (Int) -> Unit,
    ): Float {
        val left = x + Theme.PAD
        val right = x + w - Theme.PAD - 4f
        val hovered = Hits.inside(mx, my, x, y, w, Theme.ROW_HEIGHT)
        val labelY = y + (Theme.ROW_HEIGHT - Theme.BODY_SIZE) / 2f - 0.5f
        if (hovered) c.rect(x + 4f, y + 2f, w - 8f, Theme.ROW_HEIGHT - 4f, Theme.PANEL, Theme.RADIUS_SMALL)
        labelText(c, name, description, left, labelY, w - Theme.PAD * 2f - 145f, hovered)
        val bw = 100f
        val bx = right - bw
        val by = y + 8f
        val bh = 28f
        val capturingThis = capturing === owner
        val text = if (capturingThis) "Press key" else Keys.name(key)
        Widgets.button(c, bx, by, bw, bh, text, Hits.inside(mx, my, bx, by, bw, bh), capturingThis)
        addHit(hits, bx, by, bw, bh) { capturing = owner }
        addHit(hits, bx, by, bw, bh, InputConstants.MOUSE_BUTTON_RIGHT) { set(-1) }
        captureTargets[owner] = set
        return y + Theme.ROW_HEIGHT
    }

    private fun label(c: Canvas, setting: Setting<*>, x: Float, y: Float, hovered: Boolean) =
        labelText(c, setting.title, setting.description, x, y, labelWidth, hovered)

    private fun labelText(c: Canvas, title: String, description: String, x: Float, y: Float, width: Float, hovered: Boolean) {
        val titleY = if (description.isEmpty()) y else y - 6f
        c.text(UiFont.TEXT, c.trim(UiFont.TEXT, title, Theme.BODY_SIZE, width), x, titleY, Theme.BODY_SIZE, if (hovered) Theme.TEXT else Theme.TEXT_DIM)
        if (description.isNotEmpty()) {
            c.text(
                UiFont.TEXT,
                c.trim(UiFont.TEXT, description, Theme.SMALL_SIZE - 1f, width),
                x,
                y + 9f,
                Theme.SMALL_SIZE - 1f,
                Theme.TEXT_FAINT,
            )
        }
    }

    fun drawPicker(c: Canvas, hits: Hits, mx: Double = -1.0, my: Double = -1.0) {
        openList?.let { dropdown(c, it, hits, mx, my) }
        picker?.let {
            it.draw(c, hits, mx, my, pickerLabel)
        }
        if (picker != null || openList != null) return
        tooltip?.let { text ->
            val lines = c.wrap(UiFont.TEXT, text, Theme.SMALL_SIZE, 180f)
            val tw = lines.maxOf { c.textWidth(UiFont.TEXT, it, Theme.SMALL_SIZE) } + 10f
            val th = lines.size * (Theme.SMALL_SIZE + 2f) + 6f
            val tx = tooltipX.coerceAtMost(c.layoutWidth - tw - 4f)
            val ty = (tooltipY + 2f).coerceAtMost(c.layoutHeight - th - 4f)
            c.rect(tx, ty, tw, th, Theme.PANEL, Theme.RADIUS_SMALL)
            c.border(tx, ty, tw, th, Theme.BORDER_STRONG, Theme.RADIUS_SMALL)
            var ly = ty + 3f
            for (line in lines) {
                c.text(UiFont.TEXT, line, tx + 5f, ly, Theme.SMALL_SIZE, Theme.TEXT)
                ly += Theme.SMALL_SIZE + 2f
            }
        }
    }

    private fun focus(setting: TextSetting) {
        commitText()
        focusedText = setting
        textField.text = setting.value
        textField.focused = true
    }

    private fun commitText() {
        val t = focusedText ?: return
        t.value = textField.text.take(t.maxLength)
        focusedText = null
        textField.focused = false
    }

    fun dragTo(mx: Double) {
        val s = dragSlider ?: return
        val fraction = ((mx - dragX) / dragW).toFloat().coerceIn(0f, 1f)
        s.value = s.snap(s.min + (s.max - s.min) * fraction)
    }

    fun release() {
        dragSlider = null
        picker?.release()
    }

    val dragging: Boolean get() = dragSlider != null

    fun key(key: Int, modifiers: Int): Boolean {
        val target = capturing
        if (target != null) {
            captureTargets[target]?.invoke(if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) -1 else key)
            capturing = null
            return true
        }
        if (key == InputConstants.KEY_ESCAPE && openList != null) {
            openList = null
            return true
        }
        picker?.let { if (it.key(key)) return true }
        if (focusedText != null) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE) {
                commitText()
                return true
            }
            return textField.key(key, modifiers)
        }
        return false
    }

    fun char(ch: Char): Boolean {
        picker?.let { if (it.char(ch)) return true }
        return textField.char(ch)
    }

    fun clickOutside(mx: Double, my: Double) {
        commitText()
        capturing = null
        if (!Hits.inside(mx, my, dropdownDrawX, dropdownDrawY, dropdownW, dropdownHeight)) openList = null
        picker?.let {
            if (!it.contains(mx, my)) picker = null else it.unfocus()
        }
    }

    fun closePicker() {
        picker = null
    }

    val typing: Boolean get() = focusedText != null || (picker?.focused ?: false) || capturing != null

    fun scrollDropdown(mx: Double, my: Double, amount: Float): Boolean {
        if (openList == null || !Hits.inside(mx, my, dropdownDrawX, dropdownDrawY, dropdownW, dropdownHeight)) return false
        dropdownScroll = (dropdownScroll + amount).coerceIn(0f, (dropdownContent - dropdownHeight).coerceAtLeast(0f))
        return true
    }

    fun scrollBy(amount: Float) {
        scroll += amount
        picker = null
        openList = null
        tooltip = null
    }

    fun reset() {
        contentHeight = 0f
        hoverSetting = null
        tooltip = null
        captureTargets.clear()
        scroll = 0f
        scrollMotion.snap(0f)
        commitText()
        capturing = null
        picker = null
        dragSlider = null
        openList = null
    }
}
