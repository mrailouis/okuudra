package okuudra.features.impl.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import okuudra.api.input.Keybinds
import okuudra.api.world.WorldRender
import okuudra.config.Category
import okuudra.config.Feature
import okuudra.gui.ConfigScreen
import okuudra.gui.Theme

object Interface : Feature("Interface", Category.GUI, "How this menu looks and how boxes are drawn in the world.", false) {
    private val open = keybind("Open settings", InputConstants.KEY_RSHIFT).titled("Open this menu").describe("You can also type /ok.").section("Menu")
    private val scale = slider("UI scale", 1.5f, 0.5f, 3f, 0.1f).titled("Menu size").section("Menu")
    private val animation = slider("Animation time", 250f, 50f, 1000f, 10f).titled("Animation length (ms)").section("Menu")
    private val accent = color("Accent", 0xFF3260A8.toInt()).titled("Accent colour")
    val sidebarPinned = toggle("Pin sidebar", false).visibleWhen { false }
    val devMessages = toggle("Dev messages", false, "Show debug chat messages from features.").section("Misc")
    private val lineThickness = slider("Line thickness", 0.03f, 0.01f, 0.15f, 0.005f, "Thickness of world outlines and lines.").section("World rendering")

    private lateinit var slider: okuudra.config.ColorSetting
    private var migrated = false

    init {
        color("Main panel", Theme.BACKGROUND).onChange { Theme.BACKGROUND = it }
        color("Sidebar", Theme.RAIL).onChange { Theme.RAIL = it }
        color("Cards", Theme.PANEL).onChange { Theme.PANEL = it }
        color("Hover", Theme.ROW_HOVER).onChange { Theme.ROW_HOVER = it }
        color("Active", Theme.ROW_ACTIVE).onChange { Theme.ROW_ACTIVE = it }
        color("Inputs", Theme.FIELD).onChange { Theme.FIELD = it }
        color("Buttons", Theme.ELEMENT).onChange { Theme.ELEMENT = it }
        color("Separators", Theme.BORDER).onChange { Theme.BORDER = it }
        color("Borders", Theme.BORDER_STRONG).onChange { Theme.BORDER_STRONG = it }
        color("Text", Theme.TEXT).onChange { Theme.TEXT = it }
        color("Muted text", Theme.TEXT_DIM).onChange { Theme.TEXT_DIM = it }
        color("Dim text", Theme.TEXT_FAINT).onChange { Theme.TEXT_FAINT = it }
        slider = color("Slider fill", 0xFF3260A8.toInt()).onChange { Theme.SLIDER = it } as okuudra.config.ColorSetting
    }

    override fun init() {
        Keybinds.bind(open) { Minecraft.getInstance().setScreen(ConfigScreen()) }
    }

    fun tick() {
        if (!migrated) {
            migrated = true
            if (accent.value == OLD_ACCENT) accent.reset()
            if (slider.value == OLD_ACCENT) slider.reset()
            Theme.SLIDER = slider.value
        }
        Theme.accent = accent.value
        Theme.uiScale = scale.value
        Theme.animationMs = animation.value
        WorldRender.lineThickness = lineThickness.value
    }

    private const val OLD_ACCENT = 0xFFE0533D.toInt()
}
