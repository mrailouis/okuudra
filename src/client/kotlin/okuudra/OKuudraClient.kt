package okuudra

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import okuudra.api.event.EventDispatcher
import okuudra.api.hud.Hud
import okuudra.api.render.Fonts
import okuudra.api.render.Icons
import okuudra.api.render.UiPipelines
import okuudra.api.skyblock.Location
import okuudra.api.skyblock.kuudra.KuudraUtils
import okuudra.api.util.Scheduler
import okuudra.api.world.WorldRender
import okuudra.config.ConfigStore
import okuudra.features.Registry
import okuudra.features.impl.gui.Interface
import okuudra.features.impl.misc.command.Commands

object OKuudraClient : ClientModInitializer {
    override fun onInitializeClient() {
        Scheduler.init()
        UiPipelines.init()
        EventDispatcher.init()
        Location.init()
        KuudraUtils.init()
        WorldRender.init()
        Hud.init()
        Registry.init()
        ConfigStore.load()
        Commands.init()
        ClientTickEvents.END_CLIENT_TICK.register {
            Interface.tick()
            ConfigStore.tick()
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            ConfigStore.save()
            Fonts.destroy()
            Icons.destroy()
        }
        OKuudra.LOGGER.info("okuudra client ready")
    }
}
