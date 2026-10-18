package okuudra.gui

import kotlin.math.exp

class Motion(initial: Float = 0f) {
    var value = initial
        private set
    private var velocity = 0f
    private var previous = System.nanoTime()

    fun snap(target: Float) {
        value = target
        velocity = 0f
        previous = System.nanoTime()
    }

    fun toward(target: Float, duration: Float = Theme.animationMs): Float {
        val now = System.nanoTime()
        val dt = ((now - previous) / 1e9).coerceIn(0.0, 0.1).toFloat()
        previous = now
        val frequency = 6000f / duration.coerceAtLeast(1f)
        val distance = value - target
        val step = (velocity + frequency * distance) * dt
        val decay = exp(-frequency * dt)
        value = target + (distance + step) * decay
        velocity = (velocity - frequency * step) * decay
        return value
    }
}
