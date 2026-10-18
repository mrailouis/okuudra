package okuudra.features.impl.kuudra.phaseone

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.EventTick
import okuudra.api.event.on
import okuudra.api.hud.Hud
import okuudra.api.hud.HudElement
import okuudra.api.render.Canvas
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Argb
import okuudra.api.util.noControlCodes
import okuudra.api.world.WorldRender
import okuudra.config.Category
import okuudra.config.Feature
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object SupplyHighlighter : Feature("Supply Highlighter", Category.PHASE_ONE, "Highlights supplies and their hittable hitboxes.") {
    private val preSpawns = toggle("Pre spawn circles", true, "Draws a circle around supply armor stands.").section("Pre spawns")
    private val preSpawnColor = color("Pre spawn colour", Argb.withAlpha(Argb.RED, 150)).section("Pre spawns").visibleWhen { preSpawns.value }
    private val hookedColor = color("Hooked colour", Argb.withAlpha(Argb.GREEN, 150), "Colour when your bobber is inside.").section("Pre spawns").visibleWhen { preSpawns.value }

    private val giantsOn = toggle("Highlight giants", true).section("Supplies")
    private val giantColor = color("Giant colour", Argb.withAlpha(Argb.RED, 150)).section("Supplies").visibleWhen { giantsOn.value }
    private val compactGiants = toggle("Compact giant box", true, "Only highlights the top of the giant.").section("Supplies").visibleWhen { giantsOn.value }
    private val zombiesOn = toggle("Highlight zombies", true, "Highlights hittable zombies.").section("Supplies")
    private val zombieColor = color("Zombie colour", Argb.withAlpha(Argb.GREEN, 150)).section("Supplies").visibleWhen { zombiesOn.value }
    private val style = boxStyle(default = "Outline", section = "Supplies")
    private val depth = toggle("Depth", true, "Hide boxes behind walls.").section("Supplies")
    private val reachable = toggle("Reachable box", true, "Highlights the part of the crate you can reach.").section("Reach")
    private val reachableColor = color("Reachable colour", Argb.withAlpha(Argb.CYAN, 150)).section("Reach").visibleWhen { reachable.value }
    private val reach = slider("Hit distance", 3f, 3f, 6f, 0.1f).section("Reach").visibleWhen { reachable.value }
    private val doublePearl = toggle("Double pearl warning", true, "Warns when you need to double pearl.")

    private val ARMOR = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
    private val zombies = HashSet<Zombie>()
    private val giants = HashSet<Giant>()
    private val stands = HashSet<ArmorStand>()
    private var warn = false

    private val hud = object : HudElement("double_pearl", 10f, 140f) {
        override fun visible() = enabled && doublePearl.value && warn
        override fun render(c: Canvas, editing: Boolean) = label(c, "Double Pearl Needed!")
    }

    override fun init() {
        Hud.register(hud)
        on<EventTick.Start> {
            if (!KuudraUtils.inKuudra) {
                zombies.clear()
                giants.clear()
                stands.clear()
                warn = false
                return@on
            }
            val player = mc.player ?: return@on
            val level = mc.level ?: return@on
            warn = doublePearl.value && level.entitiesForRendering().any { it is Giant && it.y > 30.0 && it.y < 67.0 && blocksPearl(player.x, player.y, player.z, it) }
            if (player.tickCount % 5 != 0) return@on
            zombies.removeIf { !validSupply(it) }
            giants.removeIf { !validSupply(it) }
            stands.removeIf { !validStand(it) }
            for (entity in level.entitiesForRendering()) {
                when {
                    entity is Giant && validSupply(entity) -> giants.add(entity)
                    entity is Zombie && validSupply(entity) -> zombies.add(entity)
                    preSpawns.value && entity is ArmorStand && validStand(entity) -> stands.add(entity)
                }
            }
        }

        on<EventRenderWorld> {
            if (!KuudraUtils.inKuudra) return@on
            if (KuudraUtils.phase == 1) {
                if (giantsOn.value) giants.forEach { giantBox(render, it) }
                if (zombiesOn.value) zombies.forEach { zombieBox(render, it) }
            }
            if (preSpawns.value) {
                val bobber = mc.player?.fishing
                stands.forEach { stand ->
                    val r = 4.0
                    val hooked = bobber != null && hypot(bobber.x - stand.x, bobber.z - stand.z) <= r
                    render.circle(Vec3(stand.x, 75.1, stand.z), r, if (hooked) hookedColor.value else preSpawnColor.value)
                }
            }
        }
        super.init()
    }

    private fun validStand(entity: ArmorStand) = entity.isAlive && entity.y in 65.0..85.0 && entity.name.string.noControlCodes.equals("SUPPLIES", ignoreCase = true)

    private fun validSupply(entity: Entity): Boolean {
        if (!entity.isAlive || entity.y !in 60.0..78.0) return false
        if (entity is LivingEntity) {
            if (ARMOR.any { !entity.getItemBySlot(it).isEmpty }) return false
        }
        return true
    }

    private fun zombieBox(render: WorldRender, entity: Entity) {
        render.box(entity.boundingBox, zombieColor.value, style, depth.value)
        if (!reachable.value) return
        val player = mc.player ?: return
        val eye = Vec3(player.x, player.y + if (player.isCrouching) 1.5399999618530273 else 1.6200000047683716, player.z)
        val r = reach.value.toDouble()
        val b = entity.boundingBox
        val closest = Vec3(eye.x.coerceIn(b.minX, b.maxX), eye.y.coerceIn(b.minY, b.maxY), eye.z.coerceIn(b.minZ, b.maxZ))
        if (eye.distanceTo(closest) > r) return
        val minX = max(b.minX, eye.x - r)
        val minY = max(b.minY, eye.y - r)
        val minZ = max(b.minZ, eye.z - r)
        val maxX = min(b.maxX, eye.x + r)
        val maxY = min(b.maxY, eye.y + r)
        val maxZ = min(b.maxZ, eye.z + r)
        if (minX < maxX && minY < maxY && minZ < maxZ) render.box(AABB(minX, minY, minZ, maxX, maxY, maxZ), reachableColor.value, style, depth.value)
    }

    private fun giantBox(render: WorldRender, entity: Entity) {
        var box = entity.boundingBox
        if (compactGiants.value) box = AABB(box.minX, box.maxY - (box.maxY - box.minY) * 0.2, box.minZ, box.maxX, box.maxY, box.maxZ)
        render.box(box, giantColor.value, style, depth.value)
    }

    private fun blocksPearl(px: Double, py: Double, pz: Double, entity: Entity): Boolean {
        val eyeY = py + 1.62
        val moved = entity.y > 9000000.0
        val b = entity.boundingBox
        val minY = if (moved) 61.0 else b.minY
        val maxY = if (moved) 73.0 else b.maxY
        val minX = if (moved) entity.x - 1.8 else b.minX
        val maxX = if (moved) entity.x + 1.8 else b.maxX
        val minZ = if (moved) entity.z - 1.8 else b.minZ
        val maxZ = if (moved) entity.z + 1.8 else b.maxZ
        return px - 0.3 < maxX && px + 0.3 > minX && pz - 0.3 < maxZ && pz + 0.3 > minZ && eyeY in minY..maxY
    }
}
