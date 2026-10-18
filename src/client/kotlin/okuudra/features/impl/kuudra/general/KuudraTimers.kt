package okuudra.features.impl.kuudra.general

import okuudra.api.event.EventChat
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.toFixed
import okuudra.config.Category
import okuudra.config.Feature

object KuudraTimers : Feature("Kuudra Timers", Category.GENERAL, "Server tick timers for supply spawn and build start.") {
    private val inTicks = toggle("Show ticks", false, "Show the timers in ticks instead of seconds.")
    private val symbol = toggle("Show unit", true, "Append s or t to the timers.")
    private val supplies = toggle("Supplies timer", true)
    private val build = toggle("Build timer", true)

    private var cratesTicks = -1
    private var buildTicks = -1

    private val cratesHud = object : HudElement("supplies_timer", 10f, 60f) {
        override fun visible() = enabled && supplies.value && cratesTicks >= 0
        override fun render(c: Canvas, editing: Boolean) = label(c, "Supplies: ", format(if (editing) 180 else cratesTicks))
    }

    private val buildHud = object : HudElement("build_timer", 10f, 72f) {
        override fun visible() = enabled && build.value && buildTicks >= 0
        override fun render(c: Canvas, editing: Boolean) = label(c, "Build: ", format(if (editing) 80 else buildTicks))
    }

    override fun init() {
        Hud.register(cratesHud, buildHud)
        on<EventChat> {
            when {
                value.contains("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!") -> cratesTicks = 180
                value.contains("[NPC] Elle: OMG! Great work collecting my supplies!") -> buildTicks = 80
            }
        }
        on<EventTick.Server> {
            if (!KuudraUtils.inKuudra) return@on
            if (cratesTicks >= 0) cratesTicks--
            if (buildTicks >= 0) buildTicks--
        }
        on<EventWorld.Load> {
            cratesTicks = -1
            buildTicks = -1
        }
        super.init()
    }

    private fun format(time: Int): String =
        if (inTicks.value) "$time${if (symbol.value) "t" else ""}" else "${(time / 20f).toFixed()}${if (symbol.value) "s" else ""}"
}
