package okuudra.api.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import okuudra.OKuudra
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

object Icons {
    private class Texture(val texture: GpuTexture, val view: GpuTextureView)
    private var textures = HashMap<String, Texture>()
    private var retired = emptyList<Texture>()
    private const val MAX_TEXTURES = 96

    fun draw(c: Canvas, name: String, x: Float, y: Float, size: Float, color: Int) {
        val pixels = c.physicalSize(size)
        val texture = textures.getOrPut("$name@$pixels") { load(name, pixels) }
        c.image(texture.view, x, y, size, size, color)
    }

    private fun load(name: String, size: Int): Texture {
        val path = "/assets/${OKuudra.MOD_ID}/icons/$name.path"
        val shape = Path2D.Float(Path2D.WIND_EVEN_ODD)
        requireNotNull(Icons::class.java.getResourceAsStream(path)).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(' ')
                val values = parts.drop(1).map { it.toFloat() * size }
                when (parts[0]) {
                    "M" -> shape.moveTo(values[0], values[1])
                    "L" -> shape.lineTo(values[0], values[1])
                    "C" -> shape.curveTo(values[0], values[1], values[2], values[3], values[4], values[5])
                    "Q" -> shape.quadTo(values[0], values[1], values[2], values[3])
                    "Z" -> shape.closePath()
                }
            }
        }
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color.WHITE
            graphics.fill(shape)
        } finally {
            graphics.dispose()
        }
        val pixels = MemoryUtil.memAlloc(image.width * image.height * 4)
        try {
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    pixels.put(255.toByte()).put(255.toByte()).put(255.toByte()).put((argb ushr 24).toByte())
                }
            }
            pixels.flip()
            val device = RenderSystem.getDevice()
            val texture = device.createTexture(
                { "okuudra icon $name" },
                GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8,
                image.width,
                image.height,
                1,
                1,
            )
            device.createCommandEncoder().writeToTexture(texture, pixels, NativeImage.Format.RGBA, 0, 0, 0, 0, image.width, image.height)
            return Texture(texture, device.createTextureView(texture))
        } finally {
            MemoryUtil.memFree(pixels)
        }
    }

    fun beginFrame() {
        if (textures.size < MAX_TEXTURES) return
        close(retired)
        retired = textures.values.toList()
        textures = HashMap()
    }

    fun destroy() {
        close(textures.values)
        close(retired)
        textures.clear()
        retired = emptyList()
    }

    private fun close(values: Collection<Texture>) = values.forEach {
        it.view.close()
        it.texture.close()
    }
}
