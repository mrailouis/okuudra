package okuudra

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object OKuudra : ModInitializer {
    const val MOD_ID = "okuudra"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("okuudra loaded")
    }
}
