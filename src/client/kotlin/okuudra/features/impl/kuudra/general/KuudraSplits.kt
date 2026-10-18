package okuudra.features.impl.kuudra.general

import okuudra.api.event.EventChat
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Argb
import okuudra.api.util.legacyRuns
import okuudra.api.util.toFixed
import okuudra.api.util.toLegacy
import okuudra.config.Category
import okuudra.config.ColorSetting
import okuudra.config.Feature

object KuudraSplits : Feature("Kuudra Splits", Category.GENERAL, "Phase splits, supply pickups and freshes for the current run.") {
    private enum class Phase(val label: String, val thresholds: DoubleArray) {
        SUPPLIES("Supplies", doubleArrayOf(22.0, 23.0, 24.6, 27.0, 30.0, 34.0)),
        BUILD("Build", doubleArrayOf(12.0, 13.5, 15.0, 17.0, 19.0, 20.0)),
        EATEN("Eaten", doubleArrayOf(4.0, 4.4, 5.0, 5.5, 6.0, 7.0)),
        STUN("Stun", doubleArrayOf(0.0, 0.0, 0.0, 0.1, 0.3, 0.8)),
        DPS("DPS", doubleArrayOf(2.5, 3.2, 3.6, 3.8, 4.2, 4.5)),
        SKIP("Skip", doubleArrayOf(3.5, 4.0, 4.6, 4.8, 5.0, 5.2)),
        KILL("Kill", doubleArrayOf(1.7, 2.0, 2.3, 2.8, 3.3, 4.0)),
    }

    private val overallThresholds = doubleArrayOf(50.0, 52.49, 59.49, 65.0, 70.0, 80.0)
    private val supplyThresholds = doubleArrayOf(14.0, 16.0, 18.0, 20.0, 23.0, 26.0)
    private val freshThresholds = doubleArrayOf(4.5, 5.5, 6.5, 8.0, 10.0, 12.0)

    private val showSupplies = toggle("Show supplies", true, "Lists who placed each supply and when.")
    private val showFreshes = toggle("Show freshes", true, "Lists who got Fresh Tools and when.")

    private val titleColor = color("Title colour", 0xFFFF8F5A.toInt()).section("Labels")
    private val labelColor = color("Label colour", 0xFFFFC489.toInt()).section("Labels")
    private val indexColor = color("List number colour", 0xFFF2E6D8.toInt()).section("Labels")

    private val tiers: List<ColorSetting> = listOf(
        color("Time colour 1 (fastest)", 0xFFFFFFFF.toInt()),
        color("Time colour 2", 0xFFAA00AA.toInt()),
        color("Time colour 3", 0xFF5555FF.toInt()),
        color("Time colour 4", 0xFF55FF55.toInt()),
        color("Time colour 5", 0xFFFFAA00.toInt()),
        color("Time colour 6", 0xFFFF5555.toInt()),
        color("Time colour 7 (slowest)", 0xFFAA0000.toInt()),
    ).onEach { it.section = "Time colours" }

    private class Entry(val name: String, val seconds: Double)

    private val splits = DoubleArray(Phase.entries.size)
    private var phase: Phase? = null
    private var phaseStart = 0L
    private var supplyStart = 0L
    private var buildStart = 0L
    private var finished = false
    private val supplies = ArrayList<Entry>()
    private val freshes = ArrayList<Entry>()
    private val supplyRegex = Regex("(.+?) recovered.*?\\((\\d)/6\\)")
    private val partyFresh = Regex("Party > (?:\\[[^]]+] )?(\\w+): FRESH\\b")

    private val hud = object : HudElement("kuudra_splits", 6f, 6f) {
        override fun visible() = enabled && (phase != null || finished) && KuudraUtils.inKuudra

        override fun render(c: Canvas, editing: Boolean) {
            val lines = if (editing) example() else build()
            var y = 0f
            var w = 0f
            for (line in lines) {
                if (line.isEmpty()) {
                    y += 5f
                    continue
                }
                var x = 0f
                for ((text, color) in line) {
                    for (run in legacyRuns(text, color)) x += draw(c, run.text, run.color, run.bold, x, y)
                }
                w = maxOf(w, x)
                y += c.mcLineHeight + 1f
            }
            size(w, y)
        }
    }

    private fun draw(c: Canvas, text: String, color: Int, bold: Boolean, x: Float, y: Float): Float {
        return c.mcText(if (bold) "§l$text" else text, x, y, color, true)
    }

    private fun build(): List<List<Pair<String, Int>>> {
        val out = ArrayList<List<Pair<String, Int>>>()
        out.add(listOf("§lKuudra Splits" to titleColor.value))
        for (p in Phase.entries) {
            val t = splits[p.ordinal]
            out.add(listOf("§l${p.label} " to labelColor.value, time(t) to tier(t, p.thresholds)))
        }
        val overall = splits.sum()
        out.add(listOf("§lOverall " to labelColor.value, time(overall) to tier(overall, overallThresholds)))
        list(out, "Supplies", "${supplies.size}/6", supplies, supplyThresholds, showSupplies.value)
        list(out, "Freshes", "${freshes.size}", freshes, freshThresholds, showFreshes.value)
        return out
    }

    private fun list(out: MutableList<List<Pair<String, Int>>>, title: String, count: String, entries: List<Entry>, thresholds: DoubleArray, show: Boolean) {
        if (!show || entries.isEmpty()) return
        out.add(emptyList())
        out.add(listOf("§l$title " to titleColor.value, count to indexColor.value))
        entries.forEachIndexed { i, e ->
            out.add(listOf("${i + 1}. " to indexColor.value, "${e.name} " to Argb.WHITE, time(e.seconds) to tier(e.seconds, thresholds)))
        }
    }

    private fun example(): List<List<Pair<String, Int>>> {
        val sample = doubleArrayOf(23.06, 14.93, 5.27, 0.0, 0.40, 0.0, 0.0)
        val out = ArrayList<List<Pair<String, Int>>>()
        out.add(listOf("§lKuudra Splits" to titleColor.value))
        Phase.entries.forEach { out.add(listOf("§l${it.label} " to labelColor.value, time(sample[it.ordinal]) to tier(sample[it.ordinal], it.thresholds))) }
        out.add(listOf("§lOverall " to labelColor.value, time(43.68) to tier(43.68, overallThresholds)))
        out.add(emptyList())
        out.add(listOf("§lSupplies " to titleColor.value, "2/6" to indexColor.value))
        out.add(listOf("1. " to indexColor.value, "Example1 " to Argb.WHITE, time(14.32) to tier(14.32, supplyThresholds)))
        out.add(listOf("2. " to indexColor.value, "Example2 " to Argb.WHITE, time(20.83) to tier(20.83, supplyThresholds)))
        out.add(emptyList())
        out.add(listOf("§lFreshes " to titleColor.value, "1" to indexColor.value))
        out.add(listOf("1. " to indexColor.value, "Example3 " to Argb.WHITE, time(4.08) to tier(4.08, freshThresholds)))
        return out
    }

    private fun time(seconds: Double) = "${seconds.toFixed()}s"

    private fun tier(seconds: Double, thresholds: DoubleArray): Int {
        if (seconds <= 0) return Argb.WHITE
        val index = thresholds.indexOfFirst { seconds <= it }.let { if (it == -1) 6 else it }
        return tiers[index].value
    }

    private fun startPhase(next: Phase?) {
        val now = System.currentTimeMillis()
        phase?.let { splits[it.ordinal] = (now - phaseStart) / 1000.0 }
        phase = next
        phaseStart = now
    }

    private fun reset() {
        splits.fill(0.0)
        phase = null
        finished = false
        supplies.clear()
        freshes.clear()
    }

    override fun init() {
        Hud.register(hud)
        on<EventChat> {
            if (!KuudraUtils.inKuudra) return@on
            when {
                value.contains("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!") -> {
                    reset()
                    startPhase(Phase.SUPPLIES)
                    supplyStart = phaseStart
                }
                value.contains("[NPC] Elle: OMG! Great work collecting my supplies!") -> {
                    startPhase(Phase.BUILD)
                    buildStart = phaseStart
                }
                value.contains("[NPC] Elle: Phew! The Ballista is finally ready!") -> startPhase(Phase.EATEN)
                phase == Phase.EATEN && value.contains("has been eaten by Kuudra!") && !value.contains("Elle") -> startPhase(Phase.STUN)
                phase == Phase.STUN && value.contains("destroyed one of Kuudra's pods!") -> startPhase(Phase.DPS)
                value.contains("[NPC] Elle: POW! SURELY THAT'S IT!") -> startPhase(Phase.SKIP)
                value.contains("KUUDRA DOWN!") -> {
                    startPhase(null)
                    finished = true
                }
            }
            if (phase == Phase.SUPPLIES) supplyRegex.find(value)?.let {
                val name = component.toLegacy().substringBefore(" recovered").trim()
                supplies.add(Entry(name, (System.currentTimeMillis() - supplyStart) / 1000.0))
            }
            if (phase == Phase.BUILD) {
                val since = (System.currentTimeMillis() - buildStart) / 1000.0
                if (value.contains("Your Fresh Tools Perk bonus doubles your building speed")) {
                    freshes.add(Entry(mc.player?.displayName?.toLegacy() ?: "You", since))
                } else if (partyFresh.containsMatchIn(value)) {
                    val name = component.toLegacy().substringAfter("> ").substringBeforeLast(":").trim()
                    if (freshes.none { it.name == name }) freshes.add(Entry(name, since))
                }
            }
        }
        on<EventTick.Start> {
            val current = phase ?: return@on
            val player = mc.player ?: return@on
            if ((current == Phase.DPS || current == Phase.SKIP) && player.y < 10) startPhase(Phase.KILL)
            phase?.let { splits[it.ordinal] = (System.currentTimeMillis() - phaseStart) / 1000.0 }
        }
        on<EventWorld.Load> { reset() }
        super.init()
    }
}
