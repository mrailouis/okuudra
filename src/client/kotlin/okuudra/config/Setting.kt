package okuudra.config

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Setting<T>(val name: String, default: T, description: String) : ReadWriteProperty<Any?, T> {
    var description = description

    @Volatile
    var value: T = default
        set(v) {
            if (field != v) {
                field = v
                ConfigStore.markDirty()
                listeners.forEach { it(v) }
            }
        }
    var visible: () -> Boolean = { true }
    var section = ""
    var title = name
    var danger = false
    private val listeners = ArrayList<(T) -> Unit>()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    fun onChange(listener: (T) -> Unit): Setting<T> {
        listeners.add(listener)
        return this
    }

    var default: T = default
        private set

    fun adoptDefault() {
        default = value
    }

    fun reset() {
        value = default
    }

    abstract fun write(): JsonElement

    abstract fun read(element: JsonElement)
}

class ToggleSetting(name: String, default: Boolean, description: String) : Setting<Boolean>(name, default, description) {
    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement) {
        value = element.asBoolean
    }
}

class SliderSetting(name: String, default: Float, val min: Float, val max: Float, val step: Float, description: String) :
    Setting<Float>(name, default, description) {
    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement) {
        value = snap(element.asFloat)
    }

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(min, max)
        if (step <= 0f) return clamped
        return (Math.round((clamped - min) / step) * step + min).coerceIn(min, max)
    }

    val isInteger: Boolean get() = step >= 1f && step % 1f == 0f

    fun display(): String = if (isInteger) value.toInt().toString() else "%.${decimals}f".format(value)

    private val decimals: Int = if (step <= 0f) 2 else ceil(-log10(step.toDouble())).toInt().coerceIn(1, 3)
}

class ChoiceSetting(name: String, val options: List<String>, default: String, description: String) :
    Setting<String>(name, default, description) {
    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement) {
        val v = element.asString
        if (v in options) value = v
    }
}

class ColorSetting(name: String, default: Int, description: String) : Setting<Int>(name, default, description) {
    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement) {
        value = element.asInt
    }
}

class KeybindSetting(name: String, default: Int, description: String) : Setting<Int>(name, default, description) {
    var onPress: (() -> Unit)? = null

    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement) {
        value = element.asInt
    }

    val bound: Boolean get() = value > 0
}

class TextSetting(name: String, default: String, description: String, val maxLength: Int = 64) :
    Setting<String>(name, default, description) {
    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement) {
        value = element.asString.take(maxLength)
    }
}

class ListSetting(name: String, default: String, description: String, val options: () -> List<String>) :
    Setting<String>(name, default, description) {
    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement) {
        value = element.asString
    }
}

class ActionSetting(name: String, description: String, val label: String, val action: () -> Unit) : Setting<Unit>(name, Unit, description) {
    override fun write(): JsonElement = JsonPrimitive(0)

    override fun read(element: JsonElement) = Unit
}

class InfoSetting(name: String, val lines: List<String>) : Setting<Unit>(name, Unit, "") {
    override fun write(): JsonElement = JsonPrimitive(0)

    override fun read(element: JsonElement) = Unit
}
