package okuudra.api.event

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import okuudra.OKuudra
import okuudra.api.world.WorldRender

abstract class Event {
    open fun post(): Boolean {
        runCatching { EventBus.post(this) }.onFailure { OKuudra.LOGGER.error("Event listener failed", it) }
        return false
    }
}

abstract class EventCancellable : Event() {
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }

    override fun post(): Boolean {
        super.post()
        return isCancelled
    }
}

abstract class EventTick : Event() {
    class Start(val level: ClientLevel) : EventTick()
    class End(val level: ClientLevel) : EventTick()
    class Server : EventTick()
}

abstract class EventWorld : Event() {
    class Load : EventWorld()
    class Unload : EventWorld()
}

abstract class EventPacket(val packet: Packet<*>) : EventCancellable() {
    class Receive(packet: Packet<*>) : EventPacket(packet)
    class Send(packet: Packet<*>) : EventPacket(packet)
}

class EventChat(val value: String, val component: Component) : Event()

class EventRenderWorld(val render: WorldRender) : Event()
