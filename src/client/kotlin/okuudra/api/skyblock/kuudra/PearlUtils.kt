package okuudra.api.skyblock.kuudra

import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.client.Minecraft

object PearlUtils {

    private val mc get() = Minecraft.getInstance()

    fun predictPearlLandingFromEntity(entity: net.minecraft.world.entity.Entity): Vec3? {
        val level = mc.level ?: return null

        var px = entity.x
        var py = entity.y
        var pz = entity.z

        var vx = entity.deltaMovement.x
        var vy = entity.deltaMovement.y
        var vz = entity.deltaMovement.z

        repeat(1000) {
            val start = Vec3(px, py, pz)
            val end = Vec3(px + vx, py + vy, pz + vz)

            val hit = level.clip(
                ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)
            )

            if (hit.type == HitResult.Type.BLOCK) {
                return (hit as BlockHitResult).location
            }

            px += vx
            py += vy
            pz += vz

            vx *= 0.99
            vy *= 0.99
            vz *= 0.99
            vy -= 0.03
        }
        return null
    }

    fun predictPearlLandingFromPlayer(): Vec3? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null

        val yaw = player.yRot.toDouble()
        val pitch = player.xRot.toDouble()

        val eyeHeight = if (player.isCrouching) 1.54 else 1.62

        var px = player.x - (Math.cos(Math.toRadians(yaw)) * 0.16)
        var py = player.y + eyeHeight - 0.1 - 0.1
        var pz = player.z - (Math.sin(Math.toRadians(yaw)) * 0.16)

        val f = 0.4
        var vx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * f
        var vy = -Math.sin(Math.toRadians(pitch)) * f
        var vz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * f

        val mag = Math.sqrt(vx * vx + vy * vy + vz * vz)
        vx = (vx / mag) * 1.5
        vy = (vy / mag) * 1.5
        vz = (vz / mag) * 1.5

        repeat(1000) {
            val start = Vec3(px, py, pz)
            val end = Vec3(px + vx, py + vy, pz + vz)

            val hit = level.clip(
                ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
            )

            if (hit.type == HitResult.Type.BLOCK) {
                return (hit as BlockHitResult).location
            }

            px += vx
            py += vy
            pz += vz

            vx *= 0.99
            vy *= 0.99
            vz *= 0.99
            vy -= 0.03
        }
        return null
    }
}
