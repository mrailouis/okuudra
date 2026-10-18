package okuudra.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import okuudra.OKuudra
import okuudra.api.hud.Hud
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object ConfigStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    val dir: Path = FabricLoader.getInstance().configDir.resolve(OKuudra.MOD_ID)
    private val path: Path = dir.resolve("settings.json")
    private var dirtyAt = 0L
    private var loaded = false
    private val extras = LinkedHashMap<String, () -> JsonObject>()
    private val readers = LinkedHashMap<String, (JsonObject) -> Unit>()

    fun section(name: String, write: () -> JsonObject, read: (JsonObject) -> Unit) {
        extras[name] = write
        readers[name] = read
    }

    fun markDirty() {
        if (loaded) dirtyAt = System.currentTimeMillis()
    }

    fun tick() {
        if (dirtyAt != 0L && System.currentTimeMillis() - dirtyAt > DEBOUNCE_MILLIS) save()
    }

    fun load() {
        runCatching {
            ConfigStore::class.java.getResourceAsStream("/assets/${OKuudra.MOD_ID}/default-settings.json")?.use { stream ->
                apply(JsonParser.parseString(stream.readAllBytes().decodeToString()).asJsonObject)
                Features.all.forEach { feature -> feature.settings.forEach { it.adoptDefault() } }
                Hud.elements.forEach { it.adoptDefault() }
            }
        }.onFailure { OKuudra.LOGGER.error("Failed to load default settings", it) }
        loaded = true
        val root = runCatching { if (Files.exists(path)) JsonParser.parseString(Files.readString(path)).asJsonObject else null }
            .onFailure { OKuudra.LOGGER.error("Failed to read settings", it) }.getOrNull() ?: return
        runCatching { apply(root) }.onFailure { OKuudra.LOGGER.error("Failed to load settings", it) }
        dirtyAt = 0L
    }

    private fun apply(root: JsonObject) {
        root.getAsJsonObject("features")?.let { readFeatures(it) }
        for ((name, reader) in readers) root.getAsJsonObject(name)?.let(reader)
    }

    fun save() {
        runCatching {
            val root = JsonObject()
            root.add("features", writeFeatures())
            for ((name, writer) in extras) root.add(name, writer())
            Files.createDirectories(dir)
            val tmp = path.resolveSibling("settings.json.tmp")
            Files.writeString(tmp, gson.toJson(root))
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            dirtyAt = 0L
        }.onFailure {
            dirtyAt = System.currentTimeMillis()
            OKuudra.LOGGER.error("Failed to save settings", it)
        }
    }

    private fun readFeatures(obj: JsonObject) {
        for (feature in Features.all) {
            val f = runCatching { obj.getAsJsonObject(feature.name) }.getOrNull() ?: continue
            runCatching { f.get("keybind")?.let { feature.keybind = it.asInt } }
            val settings = runCatching { f.getAsJsonObject("settings") }.getOrNull()
            if (settings != null) {
                for (setting in feature.settings) {
                    val element = settings.get(setting.name) ?: continue
                    runCatching { setting.read(element) }
                }
            }
            if (feature.toggleable) runCatching { f.get("enabled")?.let { feature.enabled = it.asBoolean } }
        }
    }

    private fun writeFeatures(): JsonObject {
        val obj = JsonObject()
        for (feature in Features.all) {
            val f = JsonObject()
            f.addProperty("enabled", feature.enabled)
            f.addProperty("keybind", feature.keybind)
            val settings = JsonObject()
            for (setting in feature.settings) {
                if (setting is ActionSetting || setting is InfoSetting) continue
                settings.add(setting.name, setting.write())
            }
            f.add("settings", settings)
            obj.add(feature.name, f)
        }
        return obj
    }

    private const val DEBOUNCE_MILLIS = 1500L
}
