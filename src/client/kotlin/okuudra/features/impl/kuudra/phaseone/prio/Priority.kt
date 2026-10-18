package okuudra.features.impl.kuudra.phaseone.prio

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import okuudra.api.event.EventChat
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.event.onReceive
import okuudra.api.event.onSend
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.skyblock.kuudra.KuudraUtils.Crate
import okuudra.api.skyblock.kuudra.PearlUtils
import okuudra.api.util.Argb
import okuudra.api.util.Chat
import okuudra.api.util.skyblockId
import okuudra.config.Category
import okuudra.config.Feature
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Priority : Feature("Crate Priority", Category.PHASE_ONE, "Shows where to go during the supply phase from party callouts.") {
    private val alerts = toggle("Chat messages", true, "Logs your pre spot and each instruction in chat.")
    private val renderTarget = toggle("Render target", true, "Draws the pile targets after you pearl.")
    private val targetBlocks = toggle("Target blocks", true, "Boxes on the landing spot and pile targets.").visibleWhen { renderTarget.value }
    private val boxStyle = boxStyle("Box") { renderTarget.value }
    private val labels = toggle("Text labels", true, "Names above the floating targets.").visibleWhen { renderTarget.value }
    private val aimSize = slider("Aim box size", 0.1f, 0.02f, 2f, 0.01f, "Size of the floating boxes you aim at.").visibleWhen { renderTarget.value }
    private val aimDistance = slider("Aim box distance", 30f, 5f, 100f, 1f, "How far in front of you the floating boxes are drawn.").visibleWhen { renderTarget.value }
    private val labelScale = slider("Label scale", 1f, 0.25f, 5f, 0.05f, "Size of the floating box names.").visibleWhen { renderTarget.value && labels.value }
    private val alertTime = slider("Alert duration", 40f, 10f, 200f, 5f, "Ticks the HUD and alert stay on screen.")

    var lastInstruction: PriorityConfig.Instruction? = null
        private set
    private var currentPreset: Crate? = null
    private var landing: Vec3? = null
    private var landingTimer = 0
    private var pearlId: Int? = null
    private var throttle = 0
    private var hudTimer = 0
    private val calloutRegex = Regex("Party > .+: No (.+)")

    private class Spot(val name: String, val x: Double, val y: Double, val z: Double)

    private val pileSpots = mapOf(
        Crate.SQUARE to listOf(Spot("SQUARE SAFE", -139.5, 78.0, -89.5), Spot("SQUARE MIDDLE", -138.5, 78.0, -87.5)),
        Crate.XC to listOf(Spot("XC CANNON", -129.5, 78.0, -114.5), Spot("XC MIDDLE", -134.5, 78.0, -127.5), Spot("XC COAL", -133.5, 78.0, -129.5)),
        Crate.SHOP to listOf(Spot("SHOP COAL", -87.5, 78.0, -127.5), Spot("SHOP MIDDLE", -77.5, 78.0, -134.5), Spot("SHOP SAFE", -72.5, 78.0, -134.5)),
    )

    private val hud = object : HudElement("crate_priority", 10f, 160f) {
        override fun visible() = enabled && KuudraUtils.inKuudra && hudTimer > 0 && lastInstruction != null
        override fun render(c: Canvas, editing: Boolean) = label(c, if (editing) "GO X CANNON" else lastInstruction?.task ?: "")
    }

    override fun init() {
        PriorityConfig.init()
        Hud.register(hud)

        onReceive<ClientboundAddEntityPacket> {
            val player = mc.player ?: return@onReceive
            if (type == EntityType.ENDER_PEARL && player.eyePosition.distanceToSqr(Vec3(x, y, z)) < 2.0) pearlId = id
        }

        on<EventTick.Start> {
            if (hudTimer > 0) hudTimer--
            if (landingTimer <= 0 || ++throttle < 5) return@on
            throttle = 0
            val pearl = pearlId?.let { mc.level?.getEntity(it) }
            if (pearl != null) PearlUtils.predictPearlLandingFromEntity(pearl)?.let {
                landing = it
                landingTimer = 100
            }
            if (--landingTimer <= 0) {
                landing = null
                pearlId = null
            }
        }

        on<EventChat> {
            if (value.contains("[NPC] Elle: Head over to the main platform")) {
                val player = mc.player ?: return@on
                currentPreset = KuudraUtils.getPlayerPreSpot(player.x, player.z)
                if (alerts.value) Chat.message("Pre = ${currentPreset?.name}")
            }
            calloutRegex.find(value)?.let {
                val captured = it.groupValues[1].replace("!", "").uppercase().trim()
                val missing = when {
                    captured.contains("TRI") -> Crate.TRI
                    captured.contains("=") || captured.contains("EQUALS") -> Crate.EQUALS
                    captured.contains("/") || captured.contains("SLASH") -> Crate.SLASH
                    captured.contains("SHOP") -> Crate.SHOP
                    captured.contains("SQUARE") -> Crate.SQUARE
                    captured == "X" -> Crate.X
                    captured.contains("XC") || captured.contains("CANNON") -> Crate.XC
                    else -> null
                }
                val move = currentPreset?.let { pre -> missing?.let { PriorityConfig.customLogic[pre]?.get(it) } } ?: return@let
                hudTimer = alertTime.value.toInt()
                if (move == lastInstruction) return@let
                lastInstruction = move
                if (alerts.value) Chat.message(move.task)
            }
            if (value.contains("[NPC] Elle: OMG! Great work collecting my supplies!")) clear()
        }

        onSend<ServerboundUseItemPacket> {
            val player = mc.player ?: return@onSend
            if (player.mainHandItem.skyblockId != "ENDER_PEARL") return@onSend
            PearlUtils.predictPearlLandingFromPlayer()?.let {
                landing = it
                landingTimer = 100
            }
        }

        on<EventRenderWorld> {
            if (!KuudraUtils.inKuudra || !renderTarget.value) return@on
            val inst = lastInstruction ?: return@on
            val player = mc.player ?: return@on
            val land = landing ?: return@on
            val spots = pileSpots[inst.destinationPile] ?: return@on
            if (targetBlocks.value) render.box(AABB(land.x - 0.5, land.y - 1.0, land.z - 0.5, land.x + 0.5, land.y, land.z + 0.5), Argb.withAlpha(Argb.GREEN, 0.3f), boxStyle)
            val origin = Vec3(land.x, land.y + 1.54, land.z)
            for (spot in spots) {
                if (targetBlocks.value) render.box(AABB(spot.x - 0.5, spot.y, spot.z - 0.5, spot.x + 0.5, spot.y + 1.0, spot.z + 0.5), Hud.LABEL, boxStyle)
                val dx = spot.x - origin.x
                val dy = spot.y + 1.0 - origin.y
                val dz = spot.z - origin.z
                val yaw = Math.toRadians(Math.toDegrees(atan2(-dx, dz)))
                val pitch = asin(-dy / sqrt(dx * dx + dy * dy + dz * dz))
                val dist = aimDistance.value.toDouble()
                val half = aimSize.value / 2.0
                val floating = player.eyePosition.add(-sin(yaw) * cos(pitch) * dist, -sin(pitch) * dist, cos(yaw) * cos(pitch) * dist)
                render.box(AABB(floating.x - half, floating.y - half, floating.z - half, floating.x + half, floating.y + half, floating.z + half), Hud.LABEL, boxStyle)
                if (labels.value) render.text(spot.name, Vec3(floating.x, floating.y - half - 0.25 * labelScale.value, floating.z), labelScale.value, false, shadow = true)
            }
        }

        on<EventWorld.Load> {
            clear()
            landing = null
            pearlId = null
        }
        super.init()
    }

    private fun clear() {
        currentPreset = null
        lastInstruction = null
        hudTimer = 0
    }
}
