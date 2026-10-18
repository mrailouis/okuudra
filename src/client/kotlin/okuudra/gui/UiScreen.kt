package okuudra.gui

import com.mojang.blaze3d.platform.InputConstants
import okuudra.api.render.Canvas
import okuudra.api.render.UiFrame
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

abstract class UiScreen(title: String) : Screen(Component.literal(title)) {
    protected var mouseX = 0.0
    protected var mouseY = 0.0
    private var textInput = false
    protected val openedAt = System.currentTimeMillis()

    abstract fun draw(c: Canvas)

    open fun onClick(x: Double, y: Double, button: Int): Boolean = false

    open fun onRelease(x: Double, y: Double, button: Int): Boolean = false

    open fun onDrag(x: Double, y: Double, button: Int, dx: Double, dy: Double): Boolean = false

    open fun onScroll(x: Double, y: Double, dx: Double, dy: Double): Boolean = false

    open fun onKey(key: Int, scancode: Int, modifiers: Int): Boolean = false

    open fun onChar(char: Char): Boolean = false

    protected open val coordinateScale: Float get() = 1f
    protected val layoutWidth: Float get() = width / coordinateScale
    protected val layoutHeight: Float get() = height / coordinateScale

    protected fun textInput(active: Boolean) {
        if (textInput == active) return
        textInput = active
        Minecraft.getInstance().onTextInputFocusChange(this, active)
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mx: Int, my: Int, partial: Float) = Unit

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, partial: Float) {
        mouseX = mx / coordinateScale.toDouble()
        mouseY = my / coordinateScale.toDouble()
        UiFrame.draw(g) {
            it.push()
            it.scale(coordinateScale)
            try {
                draw(it)
            } finally {
                it.pop()
            }
        }
    }

    override fun tick() {
        val client = Minecraft.getInstance()
        if (client.mouseHandler.isMouseGrabbed) client.mouseHandler.releaseMouse()
    }

    override fun mouseMoved(x: Double, y: Double) {
        mouseX = x / coordinateScale
        mouseY = y / coordinateScale
        super.mouseMoved(x, y)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        mouseX = click.x() / coordinateScale
        mouseY = click.y() / coordinateScale
        return onClick(click.x() / coordinateScale, click.y() / coordinateScale, click.button()) || super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean =
        onRelease(click.x() / coordinateScale, click.y() / coordinateScale, click.button()) || super.mouseReleased(click)

    override fun mouseDragged(click: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        mouseX = click.x() / coordinateScale
        mouseY = click.y() / coordinateScale
        return onDrag(click.x() / coordinateScale, click.y() / coordinateScale, click.button(), dx / coordinateScale, dy / coordinateScale) ||
            super.mouseDragged(click, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, dx: Double, dy: Double): Boolean =
        onScroll(x / coordinateScale, y / coordinateScale, dx, dy) || super.mouseScrolled(x, y, dx, dy)

    override fun keyPressed(input: KeyEvent): Boolean {
        if (onKey(input.key(), input.scancode(), input.modifiers())) return true
        if (input.key() == InputConstants.KEY_ESCAPE) {
            onClose()
            return true
        }
        return false
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        val s = input.codepointAsString()
        return (s.isNotEmpty() && onChar(s[0])) || super.charTyped(input)
    }

    override fun removed() {
        textInput(false)
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    override fun children(): List<GuiEventListener> = emptyList()

    protected fun open(screen: Screen?) = Minecraft.getInstance().setScreen(screen)
}
