package okuudra.features.impl.kuudra.general

import net.minecraft.world.entity.decoration.ArmorStand
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.on
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Argb
import okuudra.config.Category
import okuudra.config.Feature

object KuudraQol : Feature("Kuudra QOL", Category.GENERAL, "Kuudra highlight and cannon hitbox.") {
    private val highlight = toggle("Highlight Kuudra", true, "Draws a box around Kuudra.")
    private val kuudraColor = color("Kuudra colour", Argb.RED).visibleWhen { highlight.value }
    private val kuudraStyle = boxStyle("Kuudra", default = "Outline", fill = 0.25f) { highlight.value }
    private val cannonHitbox = toggle("Cannon hitbox", true, "Outlines the cannon in phases 2 and 3.")
    private val cannonStyle = boxStyle("Cannon", default = "Outline", fill = 0.25f) { cannonHitbox.value }

    override fun init() {
        on<EventRenderWorld> {
            if (!KuudraUtils.inKuudra) return@on
            if (highlight.value) KuudraUtils.kuudraEntity?.let { render.box(it.boundingBox, kuudraColor.value, kuudraStyle) }
            if (cannonHitbox.value && KuudraUtils.phase in 2..3) {
                mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>()
                    ?.filter { it.name.string.contains("LEFT-CLICK", ignoreCase = true) }
                    ?.forEach { render.box(it.boundingBox.inflate(0.5), Argb.WHITE, cannonStyle, depth = true) }
            }
        }
        super.init()
    }
}
