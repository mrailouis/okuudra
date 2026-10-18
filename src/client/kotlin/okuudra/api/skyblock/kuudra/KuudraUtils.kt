package okuudra.api.skyblock.kuudra

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.MagmaCube
import okuudra.api.event.EventBus
import okuudra.api.event.EventChat
import okuudra.api.event.EventTick
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.event.onReceive
import okuudra.api.skyblock.Location
import okuudra.api.util.noControlCodes
import kotlin.jvm.optionals.getOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object KuudraUtils {
    enum class Crate(val chatName: String, val x: Double, val z: Double, val radiusSq: Double) {
        X("X", -142.5, -151.0, 30.0 * 30.0),
        XC("X Cannon", -143.0, -125.0, 16.0 * 16.0),
        TRI("Triangle", -67.5, -122.5, 15.0 * 15.0),
        SHOP("Shop", -81.0, -143.0, 18.0 * 18.0),
        EQUALS("Equals", -61.5, -83.5, 15.0 * 15.0),
        SLASH("Slash", -113.5, -68.5, 15.0 * 15.0),
        SQUARE("Square", -143.0, -80.0, 20.0 * 20.0),
    }

    val spawnedCrates = mutableSetOf<Crate>()
    val giantZombies = ArrayList<Giant>()
    var kuudraEntity: MagmaCube? = null
        private set
    var phase = 0
        private set
    var kuudraTier = 0
        private set

    val inKuudra: Boolean get() = Location.inKuudra

    private val tierRegex = Regex("Kuudra's Hollow \\(T(\\d)\\)$")
    private val progressRegex = Regex("\\[[|]+]\\s*(\\d+)%")
    private var lastProgress = 0L

    val pickingSupply: Boolean get() = System.currentTimeMillis() - lastProgress < 1000L

    val supplyCarriers: List<Giant> get() = giantZombies.filter { it.y < 67.0 }

    private fun onProgress(text: String) {
        if (inKuudra && phase == 1 && progressRegex.containsMatchIn(text.noControlCodes)) lastProgress = System.currentTimeMillis()
    }
    private var ticks = 0

    fun init() {
        EventBus.subscribe(this)
        on<EventTick.Start> {
            if (!inKuudra || ++ticks % 10 != 0) return@on
            scan()
        }

        onReceive<ClientboundSetActionBarTextPacket> { onProgress(text().string) }
        onReceive<ClientboundSetTitleTextPacket> { onProgress(text().string) }
        onReceive<ClientboundSetSubtitleTextPacket> { onProgress(text().string) }

        on<EventChat> {
            if (value.contains("You retrieved some of Elle's supplies") || value.contains("You moved and the Chest slipped out")) lastProgress = 0L
            if (!inKuudra) return@on
            when (value) {
                "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!" -> {
                    phase = 1
                    spawnedCrates.clear()
                }
                "[NPC] Elle: OMG! Great work collecting my supplies!" -> phase = 2
                "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> phase = 3
                "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!" -> phase = 4
            }
        }

        onReceive<ClientboundSetPlayerTeamPacket> {
            if (!inKuudra) return@onReceive
            val team = parameters.getOrNull() ?: return@onReceive
            val text = (team.playerPrefix.string + team.playerSuffix.string).noControlCodes
            tierRegex.find(text)?.groupValues?.get(1)?.let { kuudraTier = it.toInt() }
        }

        on<EventWorld.Load> {
            giantZombies.clear()
            spawnedCrates.clear()
            kuudraEntity = null
            kuudraTier = 0
            phase = 0
        }
    }

    private fun scan() {
        val entities = Minecraft.getInstance().level?.entitiesForRendering() ?: return
        giantZombies.clear()
        for (entity in entities) {
            when (entity) {
                is Giant -> {
                    if (entity.mainHandItem.hoverName.string.endsWith("Head")) {
                        giantZombies.add(entity)
                        if (phase == 1 && entity.y < 67.0) {
                            val yaw = entity.yRot
                            val cx = entity.x + 3.7 * cos((yaw + 130.0) * (PI / 180.0))
                            val cz = entity.z + 3.7 * sin((yaw + 130.0) * (PI / 180.0))
                            for (crate in Crate.entries) {
                                val dx = cx - crate.x
                                val dz = cz - crate.z
                                if (dx * dx + dz * dz < crate.radiusSq) spawnedCrates.add(crate)
                            }
                        }
                    }
                }
                is MagmaCube -> {
                    if (entity.size == 30 && entity.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100000.0) kuudraEntity = entity
                }
                is ArmorStand -> Unit
                else -> Unit
            }
        }
    }

    fun getPlayerPreSpot(x: Double, z: Double): Crate = when {
        z > -100 -> if (x > -90) Crate.EQUALS else Crate.SLASH
        else -> if (x > -90) Crate.TRI else Crate.X
    }
}
