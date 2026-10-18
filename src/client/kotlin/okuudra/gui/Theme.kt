package okuudra.gui

import okuudra.api.util.Argb

object Theme {
    var accent = 0xFF3260A8.toInt()

    var BACKGROUND = 0xFF1C1C20.toInt()
    var RAIL = 0xFF17171A.toInt()
    var PANEL = 0xFF232329.toInt()
    var ROW_HOVER = 0xFF2B2B33.toInt()
    var ROW_ACTIVE = 0xFF34343E.toInt()
    var BORDER = 0xFF2E2E36.toInt()
    var BORDER_STRONG = 0xFF454550.toInt()
    var TEXT = 0xFFEBE9F0.toInt()
    var TEXT_DIM = 0xFFB4B0BD.toInt()
    var TEXT_FAINT = 0xFF918C9E.toInt()
    var FIELD = 0xFF141417.toInt()
    var ELEMENT = 0xFF2B2B32.toInt()
    var SLIDER = 0xFF3260A8.toInt()
    var animationMs = 250f
    var uiScale = 1.5f

    const val OFF = 0xFF454550.toInt()
    const val DANGER = 0xFFD96B6B.toInt()
    const val SCRIM = 0xAA000000.toInt()

    val accentSoft: Int get() = Argb.withAlpha(accent, 28)

    const val RADIUS = 8f
    const val RADIUS_SMALL = 4f
    const val ROW_HEIGHT = 44f
    const val PAD = 20f
    const val HEADER_SIZE = 20f
    const val BODY_SIZE = 12.5f
    const val SMALL_SIZE = 11f
}
