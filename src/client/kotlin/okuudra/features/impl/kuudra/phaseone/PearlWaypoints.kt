package okuudra.features.impl.kuudra.phaseone

import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.event.onReceive
import okuudra.api.hud.Hud
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Argb
import okuudra.api.util.Chat
import okuudra.api.util.noControlCodes
import okuudra.api.util.toFixed
import okuudra.api.world.WorldRender
import okuudra.config.Category
import okuudra.config.Feature
import okuudra.features.impl.kuudra.phaseone.prio.Priority
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

object PearlWaypoints : Feature("Pearl Waypoints", Category.PHASE_ONE, "Calculates and renders pearl throws to the supply piles.") {
    private val tierNames = listOf("No Tali", "T1 Tali", "T2 Tali", "T3 Tali")
    private val talisman = choice("Talisman tier", tierNames, "No Tali", "Your Kuudra Follower Artifact tier.")
    private val inTicks = toggle("Show ticks", false, "Show timers in ticks instead of seconds.")
    private val symbol = toggle("Show unit", true, "Append s or t to the timers.")
    private val depth = toggle("Waypoint depth", true, "Hide waypoints behind walls.")

    private val doublePearl = toggle("Double pearl", true, "Second waypoint based on Crate Priority.").section("Double pearl")
    private val landingDelay = slider("Landing delay", 10f, 0f, 100f, 1f, "Ticks.").section("Double pearl").visibleWhen { doublePearl.value }
    private val dpColor = color("Double pearl colour", Hud.LABEL).section("Double pearl").visibleWhen { doublePearl.value }
    private val dpScale = slider("Double pearl box size", 0.085f, 0.01f, 2f, 0.05f).section("Double pearl").visibleWhen { doublePearl.value }
    private val dpText = toggle("Double pearl text", true, "Name and timer above the double pearl.").section("Double pearl").visibleWhen { doublePearl.value }
    private val dpTextScale = slider("Double pearl text size", 1f, 0.05f, 10f, 0.05f).section("Double pearl").visibleWhen { doublePearl.value && dpText.value }
    private val highSquare = toggle("High arc to Square", true, "Higher trajectory from Slash to Square.").section("Double pearl").visibleWhen { doublePearl.value }
    private val highXCannon = toggle("High arc to X Cannon", true, "Higher trajectory from Slash to X Cannon.").section("Double pearl").visibleWhen { doublePearl.value }

    private val render3D = toggle("3D box", true).section("Waypoints")
    private val onPile = toggle("Box on pile", false, "Draws the box on the pile instead of in front of you.").section("Waypoints").visibleWhen { render3D.value }
    private val pileScale = slider("Pile box size", 0.5f, 0.1f, 5f, 0.1f).section("Waypoints").visibleWhen { render3D.value && onPile.value }
    private val floatingScale = slider("Floating box size", 0.085f, 0.01f, 2f, 0.05f).section("Waypoints").visibleWhen { render3D.value && !onPile.value }
    private val textScale = slider("Text size", 1f, 0.05f, 10f, 0.05f).section("Waypoints")
    private val textOffset = slider("Text height offset", 0f, -2f, 2f, 0.05f).section("Waypoints")
    private val distance = slider("Projection distance", 5f, 1f, 100f, 0.5f, "Distance of the waypoint from your camera.").section("Waypoints")
    private val wpColor = color("Waypoint colour", Hud.LABEL).section("Waypoints")
    private val ping = slider("Average ping", 50f, 0f, 500f, 10f, "Ping compensation in ms.").section("Waypoints")

    private val pickTimings = arrayOf(
        intArrayOf(60, 80, 100, 120, 120),
        intArrayOf(55, 75, 90, 110, 110),
        intArrayOf(50, 65, 80, 100, 100),
        intArrayOf(45, 60, 70, 85, 85),
    )

    private class Area(val minX: Double, val minZ: Double, val maxX: Double, val maxZ: Double)
    private class Solution(val dir: Vec3, val time: Double)
    private class Waypoint(val pos: Vec3, val name: String, val time: Double, val target: Vec3)

    private val areas = mapOf(
        "x" to Area(-171.0, -183.0, -124.0, -133.0),
        "xc" to Area(-171.0, -133.0, -124.0, -103.0),
        "square" to Area(-171.0, -103.0, -124.0, -51.0),
        "slash" to Area(-124.0, -92.0, -99.0, -42.0),
        "equals" to Area(-84.0, -105.0, -33.0, -48.0),
        "tri" to Area(-78.0, -126.0, -42.0, -105.0),
        "shop" to Area(-96.0, -173.0, -42.0, -126.0),
    )

    private val piles = mapOf(
        "x" to (Vec3(-106.0, 79.0, -113.0) to "X"),
        "xc" to (Vec3(-110.0, 79.0, -106.0) to "XC"),
        "slash" to (Vec3(-106.0, 79.0, -99.0) to "/"),
        "equals" to (Vec3(-98.0, 79.0, -99.0) to "="),
        "tri" to (Vec3(-94.0, 79.0, -106.0) to "Tri"),
        "shop" to (Vec3(-98.0, 79.0, -113.0) to "Shop"),
    )

    private val dpTargets = mapOf("XCANNON" to Vec3(-129.5, 79.0, -113.0), "SQUARE" to Vec3(-140.0, 77.0, -86.5))

    private var missingPre: String? = null
    private var picking = false
    private var pickTime = 0
    private var afterPick = 0
    private val waypoints = ArrayList<Waypoint>()
    private var dp: Waypoint? = null
    private var dpTimeTillThrow = 0.0
    private val preAimDp = ArrayList<Waypoint>()

    private val gravity = Vec3(0.0, -0.03, 0.0)
    private const val DRAG = 0.01
    private const val SPEED = 1.5
    private val invPhi = 2.0 / (1.0 + Math.sqrt(5.0))

    // Hypixel sends 17 progress titles per pickup, evenly spaced across the pickup window.
    private val progressSteps = intArrayOf(5, 11, 17, 23, 29, 35, 41, 47, 53, 59, 65, 71, 77, 83, 89, 95, 100)
    private val progressRegex = Regex("^\\[[|\\s]*]\\s*(\\d+)%")
    private var progressStep = -1

    private fun maxTime(): Int {
        // Unknown tier falls back to T5, the longest window, so the timer is never early.
        val tier = if (KuudraUtils.kuudraTier in 1..5) KuudraUtils.kuudraTier - 1 else 4
        return pickTimings.getOrNull(tierNames.indexOf(talisman.value))?.getOrNull(tier) ?: 120
    }

    override fun init() {
        on<EventWorld.Load> {
            missingPre = null
            reset()
        }

        onReceive<ClientboundSystemChatPacket> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@onReceive
            val msg = content().string.noControlCodes.lowercase()
            when {
                msg.contains("no x cannon") || msg.contains("no xc") -> missingPre = "xc"
                msg.contains("no x") -> missingPre = "x"
                msg.contains("no slash") -> missingPre = "slash"
                msg.contains("no equals") -> missingPre = "equals"
                msg.contains("no triangle") || msg.contains("no tri") -> missingPre = "tri"
                msg.contains("no shop") -> missingPre = "shop"
            }
        }

        val onTitle = { msg: String ->
            val percent = progressRegex.find(msg)?.groupValues?.get(1)?.toIntOrNull()
            if (KuudraUtils.inKuudra && KuudraUtils.phase == 1 && percent != null) onProgress(percent)
        }
        onReceive<ClientboundSetActionBarTextPacket> { onTitle(text().string.noControlCodes) }
        onReceive<ClientboundSetTitleTextPacket> { onTitle(text().string.noControlCodes) }
        onReceive<ClientboundSetSubtitleTextPacket> { onTitle(text().string.noControlCodes) }

        on<EventTick.Server> { tick() }
        on<EventRenderWorld> { draw(render) }
        super.init()
    }

    private fun onProgress(percent: Int) {
        if (percent <= 0) {
            if (picking) return
            picking = true
            pickTime = 0
            progressStep = -1
            afterPick = 100
            return
        }
        val step = progressSteps.indexOfFirst { it >= percent }.let { if (it < 0) progressSteps.lastIndex else it }
        if (!picking) {
            // Missed the 0% title, start from wherever the server says we are.
            picking = true
            afterPick = 100
        } else if (step == progressStep) {
            return
        }
        progressStep = step
        pickTime = (maxTime() * (step + 1) / progressSteps.size.toDouble()).roundToInt()
    }

    private fun tick() {
        if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return reset()
        val p = mc.player ?: return
        val maxTime = maxTime()
        if (picking) {
            if (++pickTime >= maxTime) picking = false
        } else if (afterPick > 0) {
            afterPick--
        }
        val currentArea = areas.entries.firstOrNull { (_, b) -> p.x in b.minX..b.maxX && p.z in b.minZ..b.maxZ }?.key
        waypoints.clear()
        dp = null
        preAimDp.clear()
        if (currentArea == null) return

        val camera = p.position().add(0.0, if (p.isCrouching) 1.54 else 1.62, 0.0)
        val spawn = p.position().add(0.0, 1.5, 0.0)
        val area = currentArea
        val active = picking || afterPick > 0
        val wpDistance = distance.value.toDouble()

        if (area == "square") {
            val targets = if (missingPre == null) piles.values.toList() else listOfNotNull(pileFor(missingPre!!))
            for ((target, name) in targets) {
                val s = solve(spawn, target, false)
                waypoints.add(Waypoint(camera.add(s.dir.scale(wpDistance)), name, s.time, target))
            }
        } else {
            pileFor(area)?.let { (target, name) ->
                val s = solve(spawn, target, false)
                val pos = camera.add(s.dir.scale(wpDistance))
                waypoints.add(Waypoint(pos, name, s.time, pos))
            }
        }

        if (doublePearl.value) {
            val task = Priority.lastInstruction?.task
            val block = task?.let { dpBlock(it) }
            if (active && task != null && block != null) {
                val s = solve(spawn, block, highArc(area, task))
                val pos = camera.add(s.dir.scale(wpDistance))
                dpTimeTillThrow = maxTime - ping.value / 50.0 + landingDelay.value - s.time - pickTime
                dp = Waypoint(pos, "DP: ${task.replace("GO ", "")}", s.time, pos)
            } else {
                val targets = mutableListOf<String>()
                if (area != "square" && missingPre != "square") targets.add("SQUARE")
                if (area != "xc" && missingPre != "xc") targets.add("XCANNON")
                for (t in targets) {
                    val s = solve(spawn, dpTargets.getValue(t), highArc(area, t))
                    val pos = camera.add(s.dir.scale(wpDistance))
                    preAimDp.add(Waypoint(pos, "DP: $t", s.time, pos))
                }
            }
        }
    }

    private var migrated = false

    private fun draw(render: WorldRender) {
        if (!migrated) {
            migrated = true
            if (wpColor.value == Argb.argb(30, 30, 30)) wpColor.reset()
            if (dpColor.value == Argb.argb(30, 144, 255)) dpColor.reset()
        }
        if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return
        val d = depth.value
        if (waypoints.isEmpty() && dp == null && preAimDp.isEmpty()) return
        val maxTime = maxTime()
        val active = picking || afterPick > 0
        val half = floatingScale.value / 2.0
        val pad = distance.value / 25.0
        val offset = textOffset.value.toDouble()
        for (wp in waypoints) {
            val base = wp.pos.add(0.0, -half, 0.0)
            if (render3D.value) render.styledBox(if (onPile.value) cube(wp.target, pileScale.value.toDouble()) else cube(base, half), wpColor.value, "Both", d)
            val left = max(maxTime - ping.value / 50.0 - wp.time - pickTime, 0.0)
            val text = if (!active) "AIM" else if (left > 0) timer(left) else "NOW"
            render.text(wp.name, Vec3(wp.pos.x, base.y + half + pad + offset, wp.pos.z), textScale.value * LABEL_SCALE, d, shadow = true)
            render.text(text, Vec3(wp.pos.x, base.y - half - pad + offset, wp.pos.z), textScale.value * LABEL_SCALE, d, shadow = true)
        }
        val dpHalf = dpScale.value / 2.0
        for (w in preAimDp + listOfNotNull(dp)) {
            val base = w.pos.add(0.0, -dpHalf, 0.0)
            if (render3D.value) render.styledBox(cube(base, dpHalf), dpColor.value, "Both", d)
        }
        val dpWp = dp ?: return
        if (!dpText.value) return
        val base = dpWp.pos.add(0.0, -dpHalf, 0.0)
        val left = max(dpTimeTillThrow, 0.0)
        render.text(dpWp.name, Vec3(dpWp.pos.x, base.y + dpHalf + pad + offset, dpWp.pos.z), dpTextScale.value * LABEL_SCALE, d, shadow = true)
        render.text(if (left > 0) timer(left) else "NOW", Vec3(dpWp.pos.x, base.y - dpHalf - pad + offset, dpWp.pos.z), dpTextScale.value * LABEL_SCALE, d, shadow = true)
    }

    private fun cube(c: Vec3, h: Double) = AABB(c.x - h, c.y - h, c.z - h, c.x + h, c.y + h, c.z + h)

    private fun highArc(area: String, task: String) =
        area == "slash" && ((highSquare.value && task.contains("SQUARE")) || (highXCannon.value && task.contains("XCANNON")))

    private fun dpBlock(task: String): Vec3? = when {
        task.contains("X CANNON") || task.contains("XCANNON") -> dpTargets["XCANNON"]
        task.contains("SQUARE") -> dpTargets["SQUARE"]
        else -> null
    }

    private fun pileFor(area: String) = when (area) {
        "equals" -> piles["slash"]
        "slash" -> piles["equals"]
        else -> piles[area]
    }

    private fun timer(ticks: Double): String =
        if (inTicks.value) "${ticks.roundToInt()}${if (symbol.value) "t" else ""}" else "${(ticks / 20.0).toFixed()}${if (symbol.value) "s" else ""}"

    private const val LABEL_SCALE = 1.3f

    private fun reset() {
        picking = false
        pickTime = 0
        progressStep = -1
        afterPick = 0
        waypoints.clear()
        dp = null
        preAimDp.clear()
    }

    private fun velocityAt(t: Double, displacement: Vec3): Vec3 =
        displacement.scale(DRAG).subtract(gravity.scale(t)).scale(1.0 / -Math.expm1(-DRAG * t)).add(gravity.scale(1.0 / DRAG))

    private fun minimize(f: (Double) -> Double, start: Double, end: Double): Double {
        var a = start
        var b = end
        var c = b - (b - a) * invPhi
        var d = a + (b - a) * invPhi
        var fc = f(c)
        var fd = f(d)
        repeat(50) {
            if (abs(b - a) < 1e-6) return 0.5 * (a + b)
            if (fc < fd) {
                b = d
                d = c
                fd = fc
                c = b - (b - a) * invPhi
                fc = f(c)
            } else {
                a = c
                c = d
                fc = fd
                d = a + (b - a) * invPhi
                fd = f(d)
            }
        }
        return 0.5 * (a + b)
    }

    private fun solve(pos: Vec3, target: Vec3, high: Boolean): Solution {
        val delta = target.subtract(pos)
        val h = delta.y
        val flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z)
        var low = if (high) 45.0 else -89.0
        var top = if (high) 89.0 else 45.0
        var bestPitch = 0.0
        var bestTime = 0.0
        var found = false
        repeat(50) {
            val mid = (low + top) / 2.0
            val theta = Math.toRadians(mid)
            val vx = SPEED * Math.cos(theta)
            val vy = SPEED * Math.sin(theta)
            val time = flat / vx
            val y = (1.0 - Math.exp(-DRAG * time)) * vy / DRAG + gravity.y * time / DRAG
            if (y < h) {
                low = mid
            } else {
                top = mid
                bestPitch = mid
                bestTime = time
                found = true
            }
        }
        if (!found) {
            val lo = if (high) 35.0 else 1e-6
            val hi = if (high) 120.0 else 60.0
            val time = minimize({ t -> abs(velocityAt(t, delta).lengthSqr() - SPEED * SPEED) }, lo, hi)
            return Solution(velocityAt(time, delta).normalize(), time)
        }
        val theta = Math.toRadians(bestPitch)
        val yaw = Math.atan2(delta.x, delta.z)
        return Solution(Vec3(-Math.sin(yaw) * Math.cos(theta), -Math.sin(theta), Math.cos(yaw) * Math.cos(theta)).normalize(), bestTime)
    }
}
