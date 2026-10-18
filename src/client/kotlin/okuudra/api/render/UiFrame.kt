package okuudra.api.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object UiFrame {
    val canvas = Canvas()
    private var open = false

    fun draw(extractor: GuiGraphicsExtractor, body: (Canvas) -> Unit) {
        if (open) {
            body(canvas)
            return
        }
        val window = Minecraft.getInstance().window
        Fonts.beginFrame()
        Icons.beginFrame()
        canvas.begin(extractor, window.guiScale.toFloat(), extractor.guiWidth(), extractor.guiHeight())
        open = true
        try {
            body(canvas)
        } finally {
            open = false
            canvas.end()
            Fonts.upload()
        }
    }
}
