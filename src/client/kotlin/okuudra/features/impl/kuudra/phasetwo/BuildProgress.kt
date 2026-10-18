package okuudra.features.impl.kuudra.phasetwo

import net.minecraft.world.entity.decoration.ArmorStand
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.noControlCodes
import okuudra.config.Category
import okuudra.config.Feature
import kotlin.math.roundToInt

object BuildProgress : Feature("Build Progress", Category.PHASE_TWO, "Build progress worked out from the piles instead of the boss bar.") {
    private val saveFps = toggle("Save FPS", true, "Updates every 10 ticks instead of every tick.")

    private val piles = HashMap<Pair<Int, Int>, Int>()
    private var progress = 0

    private val hud = object : HudElement("build_progress", 10f, 200f) {
        override fun visible() = enabled && KuudraUtils.phase == 2
        override fun render(c: Canvas, editing: Boolean) {
            val shown = if (editing) 75 else progress
            label(c, "Build Progress: ", "$shown%")
        }
    }

    override fun init() {
        Hud.register(hud)
        on<EventWorld.Load> {
            piles.clear()
            progress = 0
        }
        on<EventTick.Start> {
            if (KuudraUtils.phase != 2) {
                piles.clear()
                progress = 0
                return@on
            }
            if (saveFps.value && (mc.player?.tickCount ?: 0) % 10 != 0) return@on
            val level = mc.level ?: return@on
            for (entity in level.entitiesForRendering()) {
                if (entity !is ArmorStand) continue
                val name = entity.customName?.string?.noControlCodes ?: continue
                if (!name.startsWith("PROGRESS:")) continue
                val key = entity.x.roundToInt() to entity.z.roundToInt()
                when {
                    name.contains("COMPLETE") -> piles[key] = 100
                    name.contains("%") -> name.substringAfter("PROGRESS:").substringBefore("%").trim().toIntOrNull()?.let { piles[key] = it }
                }
            }
            progress = piles.values.sum() / 6
        }
        super.init()
    }
}
