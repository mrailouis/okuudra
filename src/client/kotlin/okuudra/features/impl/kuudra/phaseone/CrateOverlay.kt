package okuudra.features.impl.kuudra.phaseone

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import okuudra.api.event.EventChat
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.event.onReceive
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.noControlCodes
import okuudra.config.Category
import okuudra.config.Feature
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CrateOverlay : Feature("Crate Overlay", Category.PHASE_ONE, "Smooth progress bar while picking up supplies.") {
    private val smooth = toggle("Smooth overlay", true, "Interpolates progress between server updates.")

    private const val BARS = 20
    private const val RESET_STEP = 10
    private const val BRACKET = 0xFFAAAAAA.toInt()
    private const val FILL = Hud.LABEL
    private const val EMPTY = 0xFFFFFFFF.toInt()
    private const val PERCENT = 0xFFFFFFFF.toInt()

    private var current = -1.0
    private var last = -1.0
    private var serverTicks = 0
    private var lastPick = 0
    private var ticksPerStep = RESET_STEP
    private val crateRegex = Regex("\\[[|]+]\\s*(\\d+)%")

    private val hud = object : HudElement("crate_progress", 10f, 100f) {
        override fun visible() = enabled && KuudraUtils.inKuudra && KuudraUtils.phase == 1 && current >= 0
        override fun render(c: Canvas, editing: Boolean) {
            val progress = if (editing) 93.0 else predicted()
            val filled = ((progress.coerceIn(0.0, 100.0) / 100.0) * BARS).roundToInt()
            val parts = listOf("§l[" to BRACKET, "§l" + "|".repeat(filled) to FILL, "§l" + "|".repeat(BARS - filled) to EMPTY, "§l]" to BRACKET)
            val percent = "${progress.toInt()}%"
            val barWidth = parts.sumOf { c.mcTextWidth(it.first).toDouble() }.toFloat()
            val w = max(barWidth, c.mcTextWidth(percent))
            var x = (w - barWidth) / 2f
            for ((text, color) in parts) x += c.mcText(text, x, 0f, color)
            c.mcText(percent, (w - c.mcTextWidth(percent)) / 2f, 12f, PERCENT)
            size(w, 22f)
        }
    }

    private fun predicted(): Double {
        if (!smooth.value || last < 0 || ticksPerStep <= 0 || current <= last) return current
        val since = serverTicks - lastPick
        return min(last + (current - last) * min(since, ticksPerStep) / ticksPerStep.toDouble(), current)
    }

    private fun reset() {
        current = -1.0
        last = -1.0
        ticksPerStep = RESET_STEP
    }

    override fun init() {
        Hud.register(hud)
        on<EventWorld.Load> {
            reset()
            serverTicks = 0
            lastPick = 0
        }
        on<EventTick.Start> { serverTicks++ }
        on<EventChat> {
            if (value.contains("You retrieved some of Elle's supplies") || value.contains("You moved and the Chest slipped out")) reset()
        }
        val check = { msg: String ->
            if (KuudraUtils.inKuudra && KuudraUtils.phase == 1) {
                crateRegex.find(msg)?.let { match ->
                    val percent = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (current >= 0 && percent > current) {
                        last = current
                        ticksPerStep = max(1, serverTicks - lastPick)
                    } else {
                        last = percent
                        ticksPerStep = RESET_STEP
                    }
                    current = percent
                    lastPick = serverTicks
                    mc.execute {
                        mc.gui.setOverlayMessage(Component.empty(), false)
                        mc.gui.clearTitles()
                    }
                }
            }
        }
        onReceive<ClientboundSetActionBarTextPacket> { check(text().string.noControlCodes) }
        onReceive<ClientboundSetTitleTextPacket> { check(text().string.noControlCodes) }
        onReceive<ClientboundSetSubtitleTextPacket> { check(text().string.noControlCodes) }
        super.init()
    }
}
