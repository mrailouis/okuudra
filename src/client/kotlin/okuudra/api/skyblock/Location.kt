package okuudra.api.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import okuudra.api.event.EventBus
import okuudra.api.event.EventWorld
import okuudra.api.event.on
import okuudra.api.event.onReceive

object Location {
    var area: String = ""
        private set

    val inKuudra: Boolean get() = Minecraft.getInstance().isSingleplayer || area.contains("Kuudra", ignoreCase = true)

    fun init() {
        EventBus.subscribe(this)
        onReceive<ClientboundPlayerInfoUpdatePacket> {
            if (actions().none { it == ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME }) return@onReceive
            entries().firstNotNullOfOrNull { entry ->
                entry.displayName()?.string?.takeIf { it.startsWith("Area: ") || it.startsWith("Dungeon: ") }
            }?.let { area = it.substringAfter(": ").trim() }
        }
        on<EventWorld.Load> { area = "" }
    }
}
