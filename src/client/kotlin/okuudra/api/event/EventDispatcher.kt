package okuudra.api.event

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import okuudra.api.util.noControlCodes

object EventDispatcher {
    fun init() {
        EventBus.subscribe(this)
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> EventWorld.Load().post() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> EventWorld.Unload().post() }
        ClientTickEvents.START_LEVEL_TICK.register { EventTick.Start(it).post() }
        ClientTickEvents.END_LEVEL_TICK.register { EventTick.End(it).post() }
        onReceive<ClientboundSystemChatPacket> {
            if (!overlay()) EventChat(content().string.noControlCodes, content()).post()
        }
    }
}
