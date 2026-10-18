package okuudra.features.impl.kuudra.phasethree

import net.minecraft.world.effect.MobEffects
import okuudra.api.event.EventChat
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.config.Category
import okuudra.config.Feature

object EatenTimer : Feature("Eaten Timer", Category.PHASE_THREE, "Counts down to being eaten once you get Blindness after buying a Human Cannonball.") {
    private const val COUNTDOWN_MS = 450L
    private var armed = false
    private var wasBlinded = false
    private var blindAt = 0L

    private fun remaining(): Long? {
        if (blindAt == 0L) return null
        val elapsed = System.currentTimeMillis() - blindAt
        return if (elapsed in 0..COUNTDOWN_MS) COUNTDOWN_MS - elapsed else null
    }


    private val hud = object : HudElement("eaten_timer", 10f, 220f) {
        override fun visible() = enabled && remaining() != null
        override fun render(c: Canvas, editing: Boolean) {
            val ms = if (editing) 300L else remaining() ?: 0L
            label(c, "Eaten: ", "${ms}ms")
        }
    }

    override fun init() {
        Hud.register(hud)
        on<EventChat> {
            when {
                value.contains("You purchased Human Cannonball!") -> armed = true
                value.contains("destroyed one of Kuudra's pods!") -> armed = false
            }
        }
        on<EventTick.End> {
            val blinded = mc.player?.hasEffect(MobEffects.BLINDNESS) == true
            if (armed && blinded && !wasBlinded) {
                blindAt = System.currentTimeMillis()
                armed = false
            }
            wasBlinded = blinded
        }
        on<EventWorld.Load> {
            armed = false
            wasBlinded = false
            blindAt = 0L
        }
        super.init()
    }
}
