package okuudra.api.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

fun Component.toLegacy(): String {
    val out = StringBuilder()
    visit(FormattedText.StyledContentConsumer<Unit> { style, text ->
        val rgb = style.color?.value
        ChatFormatting.entries.firstOrNull { it.isColor && it.color == rgb }?.let { out.append('§').append(it.char) }
        out.append(text)
        Optional.empty()
    }, Style.EMPTY)
    return out.toString()
}

class Run(val text: String, val color: Int, val bold: Boolean)

fun legacyRuns(text: String, base: Int): List<Run> {
    val runs = ArrayList<Run>()
    val current = StringBuilder()
    var color = base
    var bold = false
    var i = 0
    fun flush() {
        if (current.isNotEmpty()) runs.add(Run(current.toString(), color, bold))
        current.clear()
    }
    while (i < text.length) {
        val c = text[i]
        if (c == '§' && i + 1 < text.length) {
            val format = ChatFormatting.getByCode(text[i + 1])
            flush()
            when {
                format == null -> Unit
                format.isColor -> {
                    color = Argb.withAlpha(format.color ?: 0xFFFFFF, 255)
                    bold = false
                }
                format == ChatFormatting.BOLD -> bold = true
                format == ChatFormatting.RESET -> {
                    color = base
                    bold = false
                }
            }
            i += 2
            continue
        }
        current.append(c)
        i++
    }
    flush()
    return runs
}
