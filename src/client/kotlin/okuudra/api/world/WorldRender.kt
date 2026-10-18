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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import okuudra.OKuudra
import okuudra.api.event.EventRenderWorld
import okuudra.api.render.Canvas
import okuudra.api.render.UiFont
import okuudra.api.render.UiFrame
import okuudra.api.util.Argb
import okuudra.api.util.legacyRuns
import okuudra.config.BoxStyle
import org.joml.Vector3f
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

    fun box(box: AABB, color: Int, style: BoxStyle, depth: Boolean = false) {
        val o = Argb.scaleAlpha(color, style.outlineAlpha)
        val f = Argb.scaleAlpha(color, style.fillAlpha)
        if (Argb.alpha(o) == 0 && Argb.alpha(f) == 0) return
        boxes.add(Box(box, o, f, depth))
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
        if (boxes.isEmpty() && lines.isEmpty()) return
        try {
            val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
            drawFills(FILL, true, camera)
            drawFills(FILL_THROUGH, false, camera)
            drawLines(LINES, true, camera)
            drawLines(LINES_THROUGH, false, camera)
        } finally {
            boxes.clear()
            lines.clear()
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

    // Labels are projected and drawn in the HUD pass with our own font: crisper than the
    // vanilla font in world space, and shader packs can't touch them.
    private fun drawLabels(c: Canvas) {
        if (labels.isEmpty()) return
        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        val eye = camera.position()
        val forward = camera.rotation().transform(Vector3f(0f, 0f, -1f))
        val up = camera.upVector()
        for (label in labels) {
            val rel = label.pos.subtract(eye)
            if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() <= 0.05) continue
            if (label.depth && occluded(eye, label.pos)) continue
            val top = screen(label.pos)
            val worldSize = (label.scale * LABEL_WORLD_SIZE).toDouble()
            val bottom = screen(label.pos.subtract(up.x() * worldSize, up.y() * worldSize, up.z() * worldSize))
            val size = (bottom.y - top.y).toFloat().coerceIn(MIN_LABEL_SIZE, MAX_LABEL_SIZE)
            val runs = legacyRuns(label.text, Argb.WHITE)
            val width = runs.sumOf { c.textWidth(LABEL_FONT, it.text, size).toDouble() }.toFloat()
            if (top.x + width / 2f < 0f || top.x - width / 2f > c.width || top.y > c.height || top.y + size < 0f) continue
            var x = top.x.toFloat() - width / 2f
            val y = top.y.toFloat()
            val off = if (label.shadow) (size / 14f).coerceAtLeast(0.6f) else (size / 18f).coerceAtLeast(0.5f)
            val shadowAlpha = if (label.shadow) 0xE0 else 0x90
            for (run in runs) {
                c.text(LABEL_FONT, run.text, x + off, y + off, size, Argb.withAlpha(0, Argb.alpha(run.color) * shadowAlpha / 255))
                x += c.text(LABEL_FONT, run.text, x, y, size, run.color)
            }
        }
    }

    private fun screen(pos: Vec3): Vec3 {
        val mc = Minecraft.getInstance()
        val ndc = mc.gameRenderer.projectPointToScreen(pos)
        val w = mc.window.guiScaledWidth.toDouble()
        val h = mc.window.guiScaledHeight.toDouble()
        return Vec3((ndc.x * 0.5 + 0.5) * w, (0.5 - ndc.y * 0.5) * h, 0.0)
    }

    private fun occluded(eye: Vec3, pos: Vec3): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return false
        val player = mc.player ?: return false
        val hit = level.clip(ClipContext(eye, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
        return hit.type != HitResult.Type.MISS && hit.location.distanceToSqr(eye) < eye.distanceToSqr(pos) - 0.25
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
        private val LABEL_FONT = UiFont.TEXT_BOLD
        // Matches the old vanilla-font labels: 8 font pixels at 0.025 blocks each.
        private const val LABEL_WORLD_SIZE = 0.2f
        private const val MIN_LABEL_SIZE = 6f
        private const val MAX_LABEL_SIZE = 96f
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
                INSTANCE.labels.clear()
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
            HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(OKuudra.MOD_ID, "world_labels")) { extractor, _ ->
                if (Minecraft.getInstance().level != null && INSTANCE.labels.isNotEmpty()) UiFrame.draw(extractor) { INSTANCE.drawLabels(it) }
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
            IrisCompat.assign(pipeline, "BASIC")
            return RenderType.create("${OKuudra.MOD_ID}_world_$name", RenderSetup.builder(pipeline).createRenderSetup())
        }
    }
}
