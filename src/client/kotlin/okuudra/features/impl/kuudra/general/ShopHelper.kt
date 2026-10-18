package okuudra.features.impl.kuudra.general

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.on
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.config.Category
import okuudra.config.Feature
import kotlin.math.ceil
import kotlin.math.floor

object ShopHelper : Feature("Shop Helper", Category.GENERAL, "Waypoints on the shop safe spots. Yellow while you stand on one, green while you pick up a supply there.") {
    private val spotColor = color("Spot colour", 0xFF7FD4FF.toInt())
    private val standingColor = color("Standing colour", 0xFFFFE04A.toInt())
    private val pickingColor = color("Picking colour", 0xFF55FF55.toInt())

    private val spots = listOf(
        -85.30 to 78.00 to -128.30,
        -77.50 to 78.00 to -135.50,
        -75.50 to 77.50 to -136.50,
        -72.50 to 77.00 to -137.50,
        -71.50 to 77.00 to -136.50,
        -71.50 to 78.00 to -135.50,
        -70.50 to 79.00 to -134.50,
    ).map { (xy, z) -> BlockPos(floor(xy.first).toInt(), ceil(xy.second).toInt() - 1, floor(z).toInt()) }

    override fun init() {
        on<EventRenderWorld> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase !in 1..2) return@on
            val player = mc.player
            val under = player?.let { BlockPos(floor(it.x).toInt(), ceil(it.y).toInt() - 1, floor(it.z).toInt()) }
            for (spot in spots) {
                val color = when {
                    spot != under || player?.onGround() != true -> spotColor.value
                    KuudraUtils.pickingSupply -> pickingColor.value
                    else -> standingColor.value
                }
                render.box(AABB(spot), color, outline = true, fill = true)
            }
        }
        super.init()
    }
}
