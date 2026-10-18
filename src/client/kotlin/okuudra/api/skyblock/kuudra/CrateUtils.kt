package okuudra.api.skyblock.kuudra

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

fun distanceSqToHitbox(player: Player, entity: Entity): Double {
    val box1 = player.boundingBox
    val box2 = entity.boundingBox

    val dx = max(0.0, max(box1.minX - box2.maxX, box2.minX - box1.maxX))
    val dy = max(0.0, max(box1.minY - box2.maxY, box2.minY - box1.maxY))
    val dz = max(0.0, max(box1.minZ - box2.maxZ, box2.minZ - box1.maxZ))

    return dx * dx + dy * dy + dz * dz
}

fun isValidCrate(entity: Entity): Boolean {
    if (entity !is Zombie) return false
    if (!entity.isAlive) return false

    val y = entity.y
    if (y !in 60.0..78.0) return false

    val hasArmor = !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty ||
            !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty ||
            !entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty ||
            !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty
    if (hasArmor) return false
    return true
}

fun getClosestPointOnHitbox(player: Player, entity: Entity): Vec3 {
    val pBox = player.boundingBox
    val eBox = entity.boundingBox

    val closestX = when {
        pBox.maxX < eBox.minX -> eBox.minX
        pBox.minX > eBox.maxX -> eBox.maxX
        else -> (max(pBox.minX, eBox.minX) + min(pBox.maxX, eBox.maxX)) / 2.0
    }

    val closestY = when {
        pBox.maxY < eBox.minY -> eBox.minY
        pBox.minY > eBox.maxY -> eBox.maxY
        else -> (max(pBox.minY, eBox.minY) + min(pBox.maxY, eBox.maxY)) / 2.0
    }

    val closestZ = when {
        pBox.maxZ < eBox.minZ -> eBox.minZ
        pBox.minZ > eBox.maxZ -> eBox.maxZ
        else -> (max(pBox.minZ, eBox.minZ) + min(pBox.maxZ, eBox.maxZ)) / 2.0
    }

    return Vec3(closestX - entity.x, closestY - entity.y, closestZ - entity.z)
}
