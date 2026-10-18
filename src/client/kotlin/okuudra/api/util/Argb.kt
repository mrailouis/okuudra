package okuudra.api.util

import kotlin.math.abs
import kotlin.math.roundToInt

object Argb {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val RED = 0xFFFF5555.toInt()
    const val GREEN = 0xFF55FF55.toInt()
    const val AQUA = 0xFF55FFFF.toInt()
    const val CYAN = 0xFF00FFFF.toInt()
    const val MAGENTA = 0xFFFF00FF.toInt()
    const val DARK_RED = 0xFFAA0000.toInt()

    fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF

    fun withAlpha(argb: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)

    fun withAlpha(argb: Int, alpha: Float): Int = withAlpha(argb, (alpha * 255f).roundToInt())

    fun scaleAlpha(argb: Int, factor: Float): Int {
        if (factor >= 1f) return argb
        return withAlpha(argb, (alpha(argb) * factor).roundToInt())
    }

    fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a ushr shift) and 0xFF) * (1f - k) + ((b ushr shift) and 0xFF) * k).roundToInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun hex(argb: Int): String = "#%08X".format(argb)

    fun parse(text: String): Int? {
        val clean = text.trim().removePrefix("#")
        return when (clean.length) {
            6 -> clean.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> clean.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        val c = v * s
        val hh = (h % 360f + 360f) % 360f / 60f
        val x = c * (1f - abs(hh % 2f - 1f))
        val (r1, g1, b1) = when {
            hh < 1f -> Triple(c, x, 0f)
            hh < 2f -> Triple(x, c, 0f)
            hh < 3f -> Triple(0f, c, x)
            hh < 4f -> Triple(0f, x, c)
            hh < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return argb(((r1 + m) * 255f).roundToInt(), ((g1 + m) * 255f).roundToInt(), ((b1 + m) * 255f).roundToInt(), alpha)
    }

    fun toHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        val s = if (max == 0f) 0f else d / max
        return floatArrayOf((h + 360f) % 360f, s, max)
    }
}
