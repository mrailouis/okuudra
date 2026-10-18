package okuudra.api.util

import java.util.Locale

val String?.noControlCodes: String
    get() {
        val s = this ?: return ""
        if (s.indexOf('§') == -1) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '§') i += 2 else out.append(s[i++])
        }
        return out.toString()
    }

fun Number.toFixed(decimals: Int = 2): String = "%.${decimals}f".format(Locale.US, this)
