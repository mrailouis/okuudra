package okuudra.config

class BoxStyle(private val style: ChoiceSetting, private val outlineOpacity: SliderSetting, private val fillOpacity: SliderSetting) {
    val outline: Boolean get() = style.value != "Filled"
    val fill: Boolean get() = style.value != "Outline"
    val outlineAlpha: Float get() = if (outline) outlineOpacity.value else 0f
    val fillAlpha: Float get() = if (fill) fillOpacity.value else 0f

    companion object {
        val OPTIONS = listOf("Outline", "Filled", "Both")
    }
}
