package okuudra.features.impl.kuudra.phasetwo

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import okuudra.api.event.EventChat
import okuudra.api.event.EventRenderWorld
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Chat
import okuudra.api.util.noControlCodes
import okuudra.config.Category
import okuudra.config.Feature

object FreshMessages : Feature("Fresh Messages", Category.PHASE_TWO, "Announces your Fresh Tools and shows how long each teammate's fresh lasts.") {
    private val party = toggle("Party message", true, "Sends \"FRESH! (xx%)\" to party chat with the build progress.")
    private val title = toggle("Title", true, "Shows FRESH! on screen.")
    private val sound = toggle("Sound", true, "Plays a splash when you get fresh.")
    private val countdown = toggle("Countdown", true, "Shows the time left on each fresh above the player.")

    private const val FRESH_MS = 10_000L
    private val freshRegex = Regex("Party > (?:\\[[^]]+] )?(\\w+): (?:\\[IQ] )?FRESH\\b")
    private val buildRegex = Regex("Building Progress (\\d+)%")
    private val fresh = HashMap<String, Long>()

    private fun buildProgress(): Int = mc.level?.entitiesForRendering()?.firstNotNullOfOrNull {
        if (it is ArmorStand) buildRegex.find(it.name.string.noControlCodes)?.groupValues?.get(1)?.toIntOrNull() else null
    } ?: 0

    override fun init() {
        on<EventWorld.Load> { fresh.clear() }
        on<EventChat> {
            if (!KuudraUtils.inKuudra) return@on
            val now = System.currentTimeMillis()
            if (value.contains("Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!")) {
                mc.player?.let { fresh[it.name.string] = now }
                if (party.value) Chat.command("pc FRESH! (${buildProgress()}%)")
                if (title.value) Chat.title("§a§lFRESH!", 20, false)
                if (sound.value) mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_SPLASH, 1f, 2f))
                return@on
            }
            freshRegex.find(value)?.let { fresh[it.groupValues[1]] = now }
        }
        on<EventRenderWorld> {
            if (!countdown.value || fresh.isEmpty()) return@on
            val now = System.currentTimeMillis()
            fresh.entries.removeIf { now - it.value >= FRESH_MS }
            val level = mc.level ?: return@on
            val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(true).toDouble()
            for (player in level.players()) {
                val start = fresh[player.name.string] ?: continue
                val left = (FRESH_MS - (now - start)) / 1000.0
                val color = when {
                    left > 6.0 -> "§a"
                    left > 3.0 -> "§e"
                    else -> "§c"
                }
                val pos = Vec3(
                    player.xo + (player.x - player.xo) * partial,
                    player.yo + (player.y - player.yo) * partial + 2.5,
                    player.zo + (player.z - player.zo) * partial,
                )
                render.text("$color${"%.1f".format(left)}s", pos, 1f)
            }
        }
        super.init()
    }
}
