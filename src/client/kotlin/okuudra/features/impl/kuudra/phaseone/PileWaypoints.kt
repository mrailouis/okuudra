package okuudra.features.impl.kuudra.phaseone

import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import okuudra.api.event.EventChat
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Argb
import okuudra.config.Category
import okuudra.config.Feature
import kotlin.math.abs

object PileWaypoints : Feature("Pile Waypoints", Category.PHASE_ONE, "Beacons on the supply piles that still need a supply.") {
    private val normalColor = color("Pile colour", Argb.argb(255, 255, 255, 52))
    private val noPreColor = color("No pre pile colour", Argb.argb(0, 255, 144, 50), "Colour of the pile for the missing pre.")
    private val boxStyle = boxStyle(fill = 0.2f)
    private val names = toggle("Pile names", true, "Name above each pile.")

    private class Pile(val name: String, val pos: Vec3)

    private val all = listOf(
        Pile("Shop", Vec3(-98.5, 78.4, -113.5)),
        Pile("Triangle", Vec3(-94.5, 78.4, -106.5)),
        Pile("Equals", Vec3(-106.5, 78.4, -99.5)),
        Pile("Slash", Vec3(-98.5, 78.4, -99.5)),
        Pile("X Cannon", Vec3(-110.5, 78.4, -106.5)),
        Pile("X", Vec3(-106.5, 78.4, -113.5)),
    )

    private val remaining = ArrayList(all)
    private var noPre: String? = null
    private var ticks = 0

    private fun reset() {
        remaining.clear()
        remaining.addAll(all)
        noPre = null
    }

    override fun init() {
        on<EventWorld.Load> { reset() }
        on<EventChat> {
            if (value.contains("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!")) reset()
            if (!value.startsWith("Party >") || KuudraUtils.phase != 1) return@on
            val msg = value.substringAfter(": ").lowercase()
            noPre = when {
                msg.contains("no x cannon") || msg.contains("no xc") -> "X Cannon"
                msg.contains("no x") -> "X"
                msg.contains("no shop") -> "Shop"
                msg.contains("no tri") -> "Triangle"
                msg.contains("no equals") || msg.contains("no =") -> "Equals"
                msg.contains("no slash") || msg.contains("no /") -> "Slash"
                else -> noPre
            }
        }
        on<EventTick.Start> {
            if (KuudraUtils.phase != 1 || ++ticks % 5 != 0) return@on
            val level = mc.level ?: return@on
            for (stand in level.entitiesForRendering()) {
                if (stand !is ArmorStand || !stand.name.string.contains("SUPPLIES RECEIVED")) continue
                remaining.removeIf { pile ->
                    val dx = pile.pos.x - stand.x
                    val dz = pile.pos.z - stand.z
                    dx * dx + dz * dz <= 2.25 && abs(pile.pos.y - stand.y) <= 5.0
                }
            }
        }
        on<EventRenderWorld> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@on
            for (pile in remaining) {
                val color = if (pile.name == noPre) noPreColor.value else normalColor.value
                val box = AABB(pile.pos.x, pile.pos.y, pile.pos.z, pile.pos.x + 1.0, pile.pos.y + 1.0, pile.pos.z + 1.0)
                render.box(box, Argb.withAlpha(color, 255), boxStyle)
                render.beam(pile.pos.x, pile.pos.y, pile.pos.z, Argb.withAlpha(color, 255), 40)
                if (names.value) render.text(pile.name, pile.pos.add(0.5, 2.5, 0.5), 1f)
            }
        }
        super.init()
    }
}
