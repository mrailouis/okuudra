package okuudra.features.impl.kuudra.phasefour

import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Chat
import okuudra.config.Category
import okuudra.config.Feature

object KuudraDirection : Feature("Kuudra Direction", Category.PHASE_FOUR, "Shows which side Kuudra is on in phase 4 whenever it moves.") {
    private val sound = toggle("Sound", true, "Plays a ping when the direction changes.")

    private var current = ""
    private var changedAt = 0L

    override fun init() {
        on<EventWorld.Load> {
            current = ""
            changedAt = 0L
        }
        on<EventTick.Start> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 4) {
                current = ""
                return@on
            }
            val kuudra = KuudraUtils.kuudraEntity?.takeIf { it.isAlive && it.health > 0f } ?: return@on
            val direction = when {
                kuudra.x < -128 -> "§c§lRIGHT"
                kuudra.z > -84 -> "§2§lFRONT"
                kuudra.x > -72 -> "§a§lLEFT"
                kuudra.z < -132 -> "§4§lBACK"
                else -> return@on
            }
            val now = System.currentTimeMillis()
            if (direction == current || now - changedAt < 1000L) return@on
            current = direction
            changedAt = now
            Chat.title(direction, 80, sound.value)
        }
        super.init()
    }
}
