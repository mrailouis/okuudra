package okuudra.api.world

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import okuudra.OKuudra
import okuudra.api.event.EventRenderWorld
import okuudra.api.util.Argb
import java.util.Optional
import kotlin.math.cos
import kotlin.math.sin

class WorldRender private constructor() {
    private class Box(val box: AABB, val outline: Int, val fill: Int, val depth: Boolean)
    private class Line(val from: Vec3, val to: Vec3, val color: Int, val depth: Boolean)
    private class Label(val text: String, val pos: Vec3, val scale: Float, val depth: Boolean, val shadow: Boolean)

    private val boxes = ArrayList<Box>()
    private val lines = ArrayList<Line>()
    private val labels = ArrayList<Label>()
    private var submit: LevelRenderContext? = null

    fun beam(x: Double, y: Double, z: Double, color: Int, height: Int = 300) {
        val context = submit ?: return
        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera.position()
        val stack = context.poseStack()
        val time = Math.floorMod(mc.level?.gameTime ?: 0L, 40L) + mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        stack.pushPose()
        stack.translate(x - camera.x, y - camera.y, z - camera.z)
        BeaconRenderer.submitBeaconBeam(stack, context.submitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1f, time, 0, height, color, 0.2f, 0.25f)
        stack.popPose()
    }

    fun box(box: AABB, color: Int, outline: Boolean = true, fill: Boolean = false, depth: Boolean = false, fillAlpha: Float = 0.25f) {
        val o = if (outline) color else 0
        val f = if (fill) Argb.scaleAlpha(color, fillAlpha) else 0
        if (Argb.alpha(o) == 0 && Argb.alpha(f) == 0) return
        boxes.add(Box(box, o, f, depth))
    }

    fun styledBox(box: AABB, color: Int, style: String, depth: Boolean) = when (style) {
        "Filled" -> box(box, color, outline = false, fill = true, depth = depth, fillAlpha = 1f)
        "Outline" -> box(box, color, outline = true, fill = false, depth = depth)
        else -> box(box, color, outline = true, fill = true, depth = depth, fillAlpha = 0.5f)
    }

    fun line(from: Vec3, to: Vec3, color: Int, depth: Boolean = false) {
        if (Argb.alpha(color) != 0) lines.add(Line(from, to, color, depth))
    }

    fun path(points: List<Vec3>, color: Int, depth: Boolean = false) {
        for (i in 0 until points.size - 1) line(points[i], points[i + 1], color, depth)
    }

    fun circle(center: Vec3, radius: Double, color: Int, depth: Boolean = false, segments: Int = 40) {
        path((0..segments).map {
            val a = it.toDouble() / segments * Math.PI * 2
            Vec3(center.x + cos(a) * radius, center.y, center.z + sin(a) * radius)
        }, color, depth)
    }

    fun text(text: String, pos: Vec3, scale: Float = 1f, depth: Boolean = false, shadow: Boolean = false) {
        labels.add(Label(text, pos, scale, depth, shadow))
    }

    private fun flush(context: LevelRenderContext) {
        if (boxes.isEmpty() && lines.isEmpty() && labels.isEmpty()) return
        try {
            val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
            drawFills(FILL, true, camera)
            drawFills(FILL_THROUGH, false, camera)
            drawLines(LINES, true, camera)
            drawLines(LINES_THROUGH, false, camera)
            drawLabels(context, camera)
        } finally {
            boxes.clear()
            lines.clear()
            labels.clear()
        }
    }

    private fun drawFills(type: RenderType, depth: Boolean, cam: Vec3) {
        if (boxes.none { it.depth == depth && Argb.alpha(it.fill) != 0 }) return
        val builder = BufferBuilder(BUFFER, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        for (b in boxes) {
            if (b.depth != depth || Argb.alpha(b.fill) == 0) continue
            fill(builder, b.box.move(-cam.x, -cam.y, -cam.z), b.fill)
        }
        draw(type, builder)
    }

    private fun drawLines(type: RenderType, depth: Boolean, cam: Vec3) {
        val any = lines.any { it.depth == depth } || boxes.any { it.depth == depth && Argb.alpha(it.outline) != 0 }
        if (!any) return
        val half = lineThickness / 2f
        val builder = BufferBuilder(BUFFER, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        for (b in boxes) {
            if (b.depth != depth || Argb.alpha(b.outline) == 0) continue
            outline(builder, b.box.move(-cam.x, -cam.y, -cam.z), b.outline, half)
        }
        for (l in lines) {
            if (l.depth == depth) edge(builder, l.color, l.from.subtract(cam), l.to.subtract(cam), half)
        }
        draw(type, builder)
    }

    private fun draw(type: RenderType, builder: BufferBuilder) {
        builder.build()?.use { type.draw(it) }
        BUFFER.clear()
    }

    private fun drawLabels(context: LevelRenderContext, cam: Vec3) {
        if (labels.isEmpty()) return
        val mc = Minecraft.getInstance()
        val font = mc.font
        val source = context.bufferSource()
        val rotation = mc.gameRenderer.mainCamera.rotation()
        val stack = context.poseStack()
        for (label in labels) {
            stack.pushPose()
            val scale = label.scale * 0.025f
            stack.translate(label.pos.x - cam.x, label.pos.y - cam.y, label.pos.z - cam.z)
            stack.mulPose(rotation)
            stack.scale(scale, -scale, scale)
            val mode = if (label.depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH
            val x = -font.width(label.text) / 2f
            if (label.shadow) {
                val plain = label.text.replace(Regex("§."), "")
                stack.pushPose()
                stack.translate(0.0, 0.0, 0.01)
                font.drawInBatch(plain, x + 1f, 1f, 0xFF000000.toInt(), false, stack.last().pose(), source, mode, 0, FULL_BRIGHT)
                stack.popPose()
            }
            font.drawInBatch(label.text, x, 0f, -1, !label.shadow, stack.last().pose(), source, mode, 0, FULL_BRIGHT)
            stack.popPose()
        }
        source.endBatch()
    }

    private fun fill(c: VertexConsumer, b: AABB, argb: Int) {
        val x0 = b.minX.toFloat()
        val y0 = b.minY.toFloat()
        val z0 = b.minZ.toFloat()
        val x1 = b.maxX.toFloat()
        val y1 = b.maxY.toFloat()
        val z1 = b.maxZ.toFloat()
        quad(c, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1)
        quad(c, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0)
        quad(c, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0)
        quad(c, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1)
        quad(c, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0)
        quad(c, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1)
    }

    private fun quad(
        c: VertexConsumer, argb: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
    ) {
        c.addVertex(ax, ay, az).setColor(argb)
        c.addVertex(bx, by, bz).setColor(argb)
        c.addVertex(cx, cy, cz).setColor(argb)
        c.addVertex(dx, dy, dz).setColor(argb)
    }

    private fun outline(c: VertexConsumer, b: AABB, argb: Int, half: Float) {
        val v000 = Vec3(b.minX, b.minY, b.minZ)
        val v100 = Vec3(b.maxX, b.minY, b.minZ)
        val v101 = Vec3(b.maxX, b.minY, b.maxZ)
        val v001 = Vec3(b.minX, b.minY, b.maxZ)
        val v010 = Vec3(b.minX, b.maxY, b.minZ)
        val v110 = Vec3(b.maxX, b.maxY, b.minZ)
        val v111 = Vec3(b.maxX, b.maxY, b.maxZ)
        val v011 = Vec3(b.minX, b.maxY, b.maxZ)
        edge(c, argb, v000, v100, half)
        edge(c, argb, v100, v101, half)
        edge(c, argb, v101, v001, half)
        edge(c, argb, v001, v000, half)
        edge(c, argb, v010, v110, half)
        edge(c, argb, v110, v111, half)
        edge(c, argb, v111, v011, half)
        edge(c, argb, v011, v010, half)
        edge(c, argb, v000, v010, half)
        edge(c, argb, v100, v110, half)
        edge(c, argb, v101, v111, half)
        edge(c, argb, v001, v011, half)
    }

    private fun edge(c: VertexConsumer, argb: Int, from: Vec3, to: Vec3, half: Float) {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 1.0e-6) return
        val dir = delta.scale(1.0 / length)
        val mid = from.add(to).scale(0.5)
        val view = if (mid.lengthSqr() < 1.0e-6) Vec3(0.0, 1.0, 0.0) else mid.normalize()
        var perp = dir.cross(view)
        if (perp.lengthSqr() < 1.0e-6) perp = dir.cross(Vec3(0.0, 1.0, 0.0))
        if (perp.lengthSqr() < 1.0e-6) perp = dir.cross(Vec3(1.0, 0.0, 0.0))
        if (perp.lengthSqr() < 1.0e-6) return
        perp = perp.normalize().scale(half.toDouble())
        val a = from.add(perp)
        val b = from.subtract(perp)
        val cc = to.subtract(perp)
        val d = to.add(perp)
        quad(
            c, argb,
            a.x.toFloat(), a.y.toFloat(), a.z.toFloat(),
            b.x.toFloat(), b.y.toFloat(), b.z.toFloat(),
            cc.x.toFloat(), cc.y.toFloat(), cc.z.toFloat(),
            d.x.toFloat(), d.y.toFloat(), d.z.toFloat(),
        )
    }

    companion object {
        var lineThickness = 0.03f
        private const val FULL_BRIGHT = 0xF000F0
        private val INSTANCE = WorldRender()
        private val BUFFER = ByteBufferBuilder(256 * 1024)
        private val POSITION_COLOR = Identifier.withDefaultNamespace("core/position_color")
        private val FILL = type("fill", true)
        private val FILL_THROUGH = type("fill_through", false)
        private val LINES = type("lines", true)
        private val LINES_THROUGH = type("lines_through", false)

        fun init() {
            LevelRenderEvents.COLLECT_SUBMITS.register { context ->
                if (Minecraft.getInstance().level == null) return@register
                INSTANCE.submit = context
                try {
                    EventRenderWorld(INSTANCE).post()
                } finally {
                    INSTANCE.submit = null
                }
            }
            LevelRenderEvents.END_MAIN.register { context ->
                if (Minecraft.getInstance().level == null) return@register
                INSTANCE.flush(context)
            }
        }

        private fun type(name: String, depth: Boolean): RenderType {
            val pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(OKuudra.MOD_ID, "pipeline/world_$name"))
                .withVertexShader(POSITION_COLOR)
                .withFragmentShader(POSITION_COLOR)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(if (depth) Optional.of(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)) else Optional.empty())
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build()
            return RenderType.create("${OKuudra.MOD_ID}_world_$name", RenderSetup.builder(pipeline).createRenderSetup())
        }
    }
}
