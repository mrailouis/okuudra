package okuudra.api.input

import com.mojang.blaze3d.platform.InputConstants

object Keys {
    fun name(key: Int): String {
        if (key <= 0) return "None"
        return InputConstants.Type.KEYSYM.getOrCreate(key).displayName.string
    }
}
