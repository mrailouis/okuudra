package okuudra.features.impl.kuudra.phaseone.prio

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okuudra.api.skyblock.kuudra.KuudraUtils.Crate
import okuudra.config.ConfigStore
import java.nio.file.Files

object PriorityConfig {
    data class Instruction(val task: String, val destinationPile: Crate)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = ConfigStore.dir.resolve("crate-helper-config.json")

    var customLogic: MutableMap<Crate, MutableMap<Crate, Instruction>> = mutableMapOf()

    fun init() {
        loadConfig()
    }

    fun reset() {
        customLogic.clear()
        setupDefaults()
        saveConfig()
    }

    fun saveConfig() {
        runCatching {
            Files.createDirectories(configFile.parent)
            Files.writeString(configFile, gson.toJson(customLogic))
        }
    }

    private fun loadConfig() {
        try {
            setupDefaults()

            if (!Files.exists(configFile) || Files.size(configFile) == 0L) {
                saveConfig()
                return
            }

            val content = Files.readString(configFile)
            val type = object : TypeToken<MutableMap<Crate, MutableMap<Crate, Instruction>>>() {}.type
            val loadedConfig: MutableMap<Crate, MutableMap<Crate, Instruction>> = gson.fromJson(content, type) ?: mutableMapOf()

            for ((preset, instructions) in loadedConfig) {
                if (customLogic.containsKey(preset)) {
                    customLogic[preset]?.putAll(instructions)
                } else {
                    customLogic[preset] = instructions
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun setupDefaults() {
        customLogic[Crate.SLASH] = mutableMapOf(
            Crate.SLASH to Instruction("GO SHOP", Crate.SHOP),
            Crate.X to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.TRI to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.EQUALS to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SHOP to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SQUARE to Instruction("GO XCANNON", Crate.XC),
            Crate.XC to Instruction("GO SQUARE", Crate.SQUARE)
        )

        customLogic[Crate.X] = mutableMapOf(
            Crate.X to Instruction("GO SHOP", Crate.SHOP),
            Crate.TRI to Instruction("GO X CANNON", Crate.XC),
            Crate.SLASH to Instruction("GO X CANNON", Crate.XC),
            Crate.EQUALS to Instruction("GO X CANNON", Crate.XC),
            Crate.SHOP to Instruction("GO X CANNON", Crate.XC),
            Crate.SQUARE to Instruction("GO XCANNON", Crate.XC),
            Crate.XC to Instruction("GO SQUARE", Crate.SQUARE)
        )

        customLogic[Crate.TRI] = mutableMapOf(
            Crate.TRI to Instruction("GO SHOP", Crate.SHOP),
            Crate.X to Instruction("GO X CANNON", Crate.XC),
            Crate.SLASH to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.EQUALS to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SHOP to Instruction("GO X CANNON", Crate.XC),
            Crate.SQUARE to Instruction("GO SHOP", Crate.SHOP),
            Crate.XC to Instruction("GO SHOP", Crate.SHOP)
        )

        customLogic[Crate.EQUALS] = mutableMapOf(
            Crate.EQUALS to Instruction("GO SHOP", Crate.SHOP),
            Crate.X to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.TRI to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SLASH to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SHOP to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SQUARE to Instruction("GO SHOP", Crate.SHOP),
            Crate.XC to Instruction("GO SHOP", Crate.SHOP)
        )
    }
}
