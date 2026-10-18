package okuudra.api.world

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.fabricmc.loader.api.FabricLoader
import okuudra.OKuudra

// Iris refuses to draw pipelines it doesn't know about while a shader pack is active,
// so ours get mapped onto its programs. Reflection keeps Iris an optional dependency.
object IrisCompat {
    private val loaded = FabricLoader.getInstance().isModLoaded("iris")

    fun assign(pipeline: RenderPipeline, program: String) {
        if (!loaded) return
        try {
            val api = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            val programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram")
            val instance = api.getMethod("getInstance").invoke(null)
            val value = programClass.getMethod("valueOf", String::class.java).invoke(null, program)
            api.getMethod("assignPipeline", RenderPipeline::class.java, programClass).invoke(instance, pipeline, value)
        } catch (e: Throwable) {
            OKuudra.LOGGER.warn("Failed to assign pipeline {} to Iris program {}", pipeline.location, program, e)
        }
    }
}
