package okuudra.config

import net.minecraft.client.Minecraft
import okuudra.api.event.EventBus

abstract class Feature(val name: String, val category: Category, val description: String = "", val toggleable: Boolean = true) {
    val settings = ArrayList<Setting<*>>()
    protected val mc: Minecraft get() = Minecraft.getInstance()

    var keybind = -1
        set(v) {
            if (field != v) {
                field = v
                ConfigStore.markDirty()
            }
        }

    @Volatile
    var enabled = !toggleable
        set(v) {
            if (field == v) return
            field = v
            ConfigStore.markDirty()
            if (v) onEnable() else onDisable()
        }

    open fun onEnable() {
        EventBus.subscribe(this)
    }

    open fun onDisable() {
        EventBus.unsubscribe(this)
    }

    open fun init() {
        if (enabled) EventBus.subscribe(this)
    }

    fun toggle() {
        if (toggleable) enabled = !enabled
    }

    protected fun <S : Setting<*>> add(setting: S): S {
        settings.add(setting)
        return setting
    }

    protected fun toggle(name: String, default: Boolean, description: String = "") = add(ToggleSetting(name, default, description))

    protected fun slider(name: String, default: Float, min: Float, max: Float, step: Float = 0f, description: String = "") =
        add(SliderSetting(name, default, min, max, step, description))

    protected fun choice(name: String, options: List<String>, default: String = options.first(), description: String = "") =
        add(ChoiceSetting(name, options, default, description))

    protected fun color(name: String, default: Int, description: String = "") = add(ColorSetting(name, default, description))

    protected fun keybind(name: String, default: Int = -1, description: String = "") = add(KeybindSetting(name, default, description))

    protected fun text(name: String, default: String, description: String = "", maxLength: Int = 64) =
        add(TextSetting(name, default, description, maxLength))

    protected fun list(name: String, default: String, description: String = "", options: () -> List<String>) =
        add(ListSetting(name, default, description, options))

    protected fun action(name: String, label: String = "Run", description: String = "", action: () -> Unit) =
        add(ActionSetting(name, description, label, action))

    protected fun info(name: String, vararg lines: String) = add(InfoSetting(name, lines.toList()))

    protected fun <S : Setting<*>> S.section(name: String): S = apply { section = name }

    protected fun <S : Setting<*>> S.titled(text: String): S = apply { title = text }

    protected fun <S : Setting<*>> S.danger(): S = apply { danger = true }

    protected fun <S : Setting<*>> S.describe(text: String): S = apply { description = text }

    protected fun <T, S : Setting<T>> S.visibleWhen(condition: () -> Boolean): S {
        visible = condition
        return this
    }
}
