package okuudra.features.impl.kuudra.phasetwo

import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.EventTick
import okuudra.api.event.on
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Argb
import okuudra.api.util.noControlCodes
import okuudra.api.util.toLegacy
import okuudra.config.Category
import okuudra.config.Feature

object BuildWaypoints : Feature("Build Waypoints", Category.PHASE_TWO, "Beacons on the build piles, coloured by how far each one is built.") {
    private val opacity = slider("Box opacity", 0.35f, 0.05f, 1f, 0.05f)
    private val showText = toggle("Progress text", true, "Shows the pile progress above it.")

    private class Pile(val pos: Vec3, val name: String, val progress: Int)

    private val piles = ArrayList<Pile>()
    private val progressRegex = Regex("PROGRESS:\\s*(\\d+)%")
    private var ticks = 0

    private val stops = listOf(
        0f to Argb.argb(168, 0, 0),
        25f to Argb.argb(255, 0, 0),
        50f to Argb.argb(255, 135, 0),
        75f to Argb.argb(46, 130, 0),
        100f to Argb.argb(125, 218, 88),
    )

    private fun color(progress: Int): Int {
        val p = progress.coerceIn(0, 100).toFloat()
        val upper = stops.indexOfFirst { it.first >= p }.coerceAtLeast(1)
        val (from, a) = stops[upper - 1]
        val (to, b) = stops[upper]
        return Argb.mix(a, b, (p - from) / (to - from))
    }

    override fun init() {
        on<EventTick.Start> {
            if (KuudraUtils.phase != 2) {
                piles.clear()
                return@on
            }
            if (++ticks % 2 != 0) return@on
            val level = mc.level ?: return@on
            piles.clear()
            for (stand in level.entitiesForRendering()) {
                if (stand !is ArmorStand) continue
                val name = stand.customName ?: continue
                val progress = progressRegex.find(name.string.noControlCodes)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                piles.add(Pile(stand.position(), name.toLegacy(), progress))
            }
        }
        on<EventRenderWorld> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 2) return@on
            for (pile in piles) {
                val color = color(pile.progress)
                val x = pile.pos.x - 0.5
                val z = pile.pos.z - 0.5
                val y = pile.pos.y - 1.0
                render.box(AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0), color, outline = true, fill = true, fillAlpha = opacity.value)
                render.beam(x, y, z, color, 25)
                if (showText.value) render.text(pile.name, pile.pos.add(0.0, 1.0, 0.0), 1f)
            }
        }
        super.init()
    }
}
