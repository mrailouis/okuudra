package okuudra.api.input

import net.minecraft.client.Minecraft
import okuudra.config.Features
import okuudra.config.KeybindSetting

object Keybinds {
    private val actions = HashMap<KeybindSetting, () -> Unit>()

    fun bind(setting: KeybindSetting, action: () -> Unit) {
        actions[setting] = action
    }

    fun onPress(key: Int): Boolean {
        if (Minecraft.getInstance().screen != null || key <= 0) return false
        var handled = false
        for (feature in Features.all) {
            if (feature.keybind == key) {
                feature.toggle()
                handled = true
            }
            if (!feature.enabled) continue
            for (setting in feature.settings) {
                if (setting is KeybindSetting && setting.value == key) {
                    actions[setting]?.invoke()
                    handled = true
                }
            }
        }
        return handled
    }
}
