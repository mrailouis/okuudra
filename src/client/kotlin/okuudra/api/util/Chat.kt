package okuudra.api.util

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import okuudra.features.impl.gui.Interface

object Chat {
    private const val PREFIX = "§8[§9oKuudra§8]§r "

    private val mc get() = Minecraft.getInstance()

    fun message(text: String) {
        mc.execute { mc.gui.chat.addClientSystemMessage(Component.literal(PREFIX + text)) }
    }

    fun success(text: String) = message("§a$text")

    fun error(text: String) = message("§c$text")

    fun dev(text: String) {
        if (Interface.devMessages.value) message("§7$text")
    }

    fun command(command: String) {
        mc.execute { mc.player?.connection?.sendCommand(command) }
    }

    fun title(text: String, stay: Int = 20, sound: Boolean = true) {
        mc.execute {
            mc.gui.setTimes(0, stay, 5)
            mc.gui.setTitle(Component.literal(text))
            if (sound) mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1f))
        }
    }

    fun attempt(done: String? = null, action: () -> Unit): Int = try {
        action()
        done?.let(::success)
        1
    } catch (failure: Exception) {
        error(failure.message ?: "Operation failed")
        0
    }
}
