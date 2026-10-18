package okuudra.features.impl.kuudra.phaseone

import net.minecraft.world.phys.Vec3
import okuudra.api.event.EventChat
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Chat
import okuudra.config.Category
import okuudra.config.Feature
import kotlin.math.cos
import kotlin.math.sin

object NoPreAlert : Feature("No Pre Alert", Category.PHASE_ONE, "Tells your party when the supply at your pre spot did not spawn.") {
    private class Pre(val name: String, val location: Vec3, val radius: Double, val second: String?, val secondLocation: Vec3?, val secondRadius: Double)

    private val pres = listOf(
        Pre("Triangle", Vec3(-67.5, 77.0, -122.5), 15.0, "Shop", Vec3(-81.0, 76.0, -143.0), 18.0),
        Pre("X", Vec3(-142.5, 77.0, -151.0), 30.0, "X Cannon", Vec3(-143.0, 76.0, -125.0), 16.0),
        Pre("Equals", Vec3(-65.5, 76.0, -87.5), 15.0, null, null, 0.0),
        Pre("Slash", Vec3(-113.5, 77.0, -68.5), 15.0, "Square", Vec3(-143.0, 76.0, -80.0), 20.0),
    )

    private const val PRE_RADIUS = 18.0
    private const val SPAWN_GRACE_MS = 500L
    private const val FALLBACK_MS = 10_000L

    private var pre: Pre? = null
    private var phaseStart = 0L
    private var firstSeen = 0L
    private var checked = false
    private val missing = ArrayList<String>()

    private val hud = object : HudElement("no_pre", 10f, 120f) {
        override fun visible() = enabled && KuudraUtils.inKuudra && KuudraUtils.phase == 1 && missing.isNotEmpty()
        override fun render(c: Canvas, editing: Boolean) = label(c, "No pre: ", if (editing) "Triangle" else missing.joinToString(", "))
    }

    private fun reset() {
        pre = null
        phaseStart = 0L
        firstSeen = 0L
        checked = false
        missing.clear()
    }

    private val calloutRegex = Regex("^Party > .+?: No (.+)$")

    private fun callout(raw: String): String? {
        val text = raw.replace("!", "").trim().uppercase()
        return when {
            text.startsWith("XC") || text.contains("CANNON") -> "X Cannon"
            text == "X" -> "X"
            text.startsWith("TRI") -> "Triangle"
            text.startsWith("SHOP") -> "Shop"
            text.startsWith("EQUALS") || text == "=" -> "Equals"
            text.startsWith("SLASH") || text == "/" -> "Slash"
            text.startsWith("SQUARE") -> "Square"
            else -> null
        }
    }

    private fun detect(pos: Vec3): Pre? = pres.firstOrNull { pos.distanceToSqr(it.location) < it.radius * it.radius }

    override fun init() {
        Hud.register(hud)
        on<EventWorld.Load> { reset() }

        on<EventChat> {
            when {
                value.contains("[NPC] Elle: Head over to the main platform") -> mc.player?.let { pre = detect(it.position()) }
                value.contains("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!") -> {
                    val keep = pre
                    reset()
                    pre = keep
                    phaseStart = System.currentTimeMillis()
                }
                value.contains("[NPC] Elle: Not again!") -> check()
            }
            if (KuudraUtils.phase == 1) calloutRegex.find(value)?.let { match -> callout(match.groupValues[1])?.let { if (it !in missing) missing.add(it) } }
        }

        on<EventTick.Start> {
            if (checked || phaseStart == 0L || KuudraUtils.phase != 1) return@on
            val now = System.currentTimeMillis()
            if (KuudraUtils.supplyCarriers.isNotEmpty()) {
                if (firstSeen == 0L) firstSeen = now
                if (now - firstSeen >= SPAWN_GRACE_MS) check()
            } else if (now - phaseStart >= FALLBACK_MS) {
                check()
            }
        }
        super.init()
    }

    private fun check() {
        if (checked || phaseStart == 0L || !KuudraUtils.inKuudra) return
        val player = mc.player ?: return
        val spot = pre ?: detect(player.position())
        checked = true
        if (spot == null) return Chat.error("Could not work out your pre spot")
        pre = spot
        val crates = KuudraUtils.supplyCarriers.map {
            val angle = Math.toRadians(it.yRot + 130.0)
            Vec3(it.x + 3.7 * cos(angle), 75.0, it.z + 3.7 * sin(angle))
        }
        fun near(target: Vec3, radius: Double) = crates.any { it.distanceToSqr(Vec3(target.x, 75.0, target.z)) < radius * radius }
        val found = ArrayList<String>()
        if (!near(spot.location, PRE_RADIUS)) found.add(spot.name)
        if (spot.second != null && spot.secondLocation != null && !near(spot.secondLocation, spot.secondRadius)) found.add(spot.second)
        for (name in found) {
            if (name !in missing) missing.add(name)
            Chat.command("pc No $name!")
        }
    }
}
