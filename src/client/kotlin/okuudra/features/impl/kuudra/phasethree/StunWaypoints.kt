package okuudra.features.impl.kuudra.phasethree

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

object StunWaypoints : Feature("Stun Waypoints", Category.PHASE_THREE, "Shows the three pod blocks inside Kuudra, offset to where they will be before you are eaten.") {
    private val color = color("Waypoint colour", Argb.argb(0, 245, 255, 200))
    private val style = boxStyle(default = "Outline")
    private val depth = toggle("Depth", false, "Hide the waypoints behind walls.")

    private val entrance = Vec3(-161.0, 49.0, -186.0)
    private val pods = listOf(Vec3(-167.5, 28.0, -167.5), Vec3(-152.5, 27.0, -172.5), Vec3(-154.5, 28.0, -156.5))

    private var cannonball = false
    private var eaten = false

    override fun init() {
        on<EventWorld.Load> {
            cannonball = false
            eaten = false
        }
        on<EventChat> {
            if (value.contains("You purchased Human Cannonball!")) cannonball = true
            if (value.contains("destroyed one of Kuudra's pods!")) {
                cannonball = false
                eaten = false
            }
        }
        on<EventTick.Start> {
            if (cannonball && (mc.player?.y ?: 100.0) < 50.0) {
                cannonball = false
                eaten = true
            }
        }
        on<EventRenderWorld> {
            if (!KuudraUtils.inKuudra || (!cannonball && !eaten)) return@on
            val player = mc.player ?: return@on
            val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(true).toDouble()
            val pos = Vec3(player.xo + (player.x - player.xo) * partial, player.yo + (player.y - player.yo) * partial, player.zo + (player.z - player.zo) * partial)
            val offset = if (cannonball) pos.subtract(entrance) else Vec3.ZERO
            for (pod in pods) {
                val p = pod.add(offset)
                render.box(AABB(p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 1.0, p.z + 0.5), color.value, style, depth.value)
            }
        }
        super.init()
    }
}
