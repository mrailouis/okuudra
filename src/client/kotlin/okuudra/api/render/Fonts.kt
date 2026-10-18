package okuudra.api.render

import okuudra.OKuudra

enum class UiFont(val file: String, val extension: String = "otf") {
    DISPLAY("DisplayRegular"),
    DISPLAY_SEMIBOLD("DisplaySemibold"),
    DISPLAY_BOLD("DisplayBold"),
    TEXT("TextRegular"),
    TEXT_SEMIBOLD("TextSemibold"),
    TEXT_BOLD("TextBold"),
    MONO("JetBrainsMonoNerd", "ttf"),
}

object Fonts {
    private val atlases = HashMap<UiFont, FontAtlas>()

    fun atlas(font: UiFont): FontAtlas? = atlases.getOrPut(font) { load(font) ?: return null }

    private fun load(font: UiFont): FontAtlas? {
        val path = "/assets/${OKuudra.MOD_ID}/fonts/${font.file}.${font.extension}"
        val bytes = Fonts::class.java.getResourceAsStream(path)?.use { it.readAllBytes() }
        if (bytes == null) {
            OKuudra.LOGGER.warn("Missing font {}", path)
            return null
        }
        return FontAtlas(bytes)
    }

    fun beginFrame() = atlases.values.forEach { it.beginFrame() }

    fun upload() = atlases.values.forEach { it.upload() }

    fun destroy() {
        atlases.values.forEach { it.destroy() }
        atlases.clear()
    }
}
