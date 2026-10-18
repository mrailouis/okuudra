package okuudra.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft

class TextField(val maxLength: Int = 64, private val filter: (Char) -> Boolean = { true }) {
    var text = ""
        set(v) {
            field = v.take(maxLength)
        }
    var focused = false
    var onChange: () -> Unit = {}
    var onSubmit: () -> Unit = {}

    fun key(key: Int, modifiers: Int): Boolean {
        if (!focused) return false
        when (key) {
            InputConstants.KEY_BACKSPACE -> if (text.isNotEmpty()) {
                text = text.dropLast(1)
                onChange()
            }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> onSubmit()
            InputConstants.KEY_ESCAPE -> focused = false
            InputConstants.KEY_V -> if (modifiers and PASTE_MODIFIERS != 0) {
                val clip = Minecraft.getInstance().keyboardHandler.clipboard.filter(filter)
                text += clip
                onChange()
            } else {
                return false
            }
            else -> return false
        }
        return true
    }

    fun char(ch: Char): Boolean {
        if (!focused || !filter(ch) || text.length >= maxLength) return false
        text += ch
        onChange()
        return true
    }

    fun clear() {
        text = ""
        onChange()
    }

    companion object {
        private const val PASTE_MODIFIERS = InputConstants.MOD_CONTROL or InputConstants.MOD_SUPER
    }
}
