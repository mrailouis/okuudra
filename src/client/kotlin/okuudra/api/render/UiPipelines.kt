package okuudra.api.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import net.minecraft.resources.Identifier
import okuudra.OKuudra

object UiPipelines {
    val SHAPE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .add("UV2", VertexFormatElement.UV2)
        .build()

    val SHAPE: RenderPipeline = RenderPipeline.builder()
        .withLocation(id("pipeline/ui_rect"))
        .withVertexShader(id("core/ui_rect"))
        .withFragmentShader(id("core/ui_rect"))
        .withUniform("Globals", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
        .withCull(false)
        .build()

    fun init() = Unit

    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(OKuudra.MOD_ID, path)
}
