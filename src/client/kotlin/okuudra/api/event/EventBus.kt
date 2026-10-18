package okuudra.api.event

import net.minecraft.network.protocol.Packet

object EventBus {
    class Listener(val owner: Class<*>, val priority: Int, val handler: (Event) -> Unit)

    private val listeners = HashMap<Class<out Event>, MutableList<Listener>>()
    private val active = HashSet<Class<*>>()
    private val cache = HashMap<Class<out Event>, Array<Listener>>()

    fun subscribe(owner: Any) {
        if (active.add(owner.javaClass)) cache.clear()
    }

    fun unsubscribe(owner: Any) {
        if (active.remove(owner.javaClass)) cache.clear()
    }

    fun register(owner: Class<*>, type: Class<out Event>, priority: Int, handler: (Event) -> Unit) {
        val list = listeners.getOrPut(type) { ArrayList() }
        list.add(Listener(owner, priority, handler))
        list.sortByDescending { it.priority }
        cache.clear()
    }

    fun post(event: Event) {
        val handlers = cache.getOrPut(event.javaClass) {
            listeners[event.javaClass]?.filter { it.owner in active }?.toTypedArray() ?: emptyArray()
        }
        for (listener in handlers) listener.handler(event)
    }
}

inline fun <reified T : Event> Any.on(priority: Int = 0, noinline handler: T.() -> Unit) =
    EventBus.register(this.javaClass, T::class.java, priority) { (it as T).handler() }

inline fun <reified P : Packet<*>> Any.onReceive(priority: Int = 0, noinline handler: P.(EventPacket.Receive) -> Unit) =
    EventBus.register(this.javaClass, EventPacket.Receive::class.java, priority) { e ->
        e as EventPacket.Receive
        (e.packet as? P)?.handler(e)
    }

inline fun <reified P : Packet<*>> Any.onSend(priority: Int = 0, noinline handler: P.(EventPacket.Send) -> Unit) =
    EventBus.register(this.javaClass, EventPacket.Send::class.java, priority) { e ->
        e as EventPacket.Send
        (e.packet as? P)?.handler(e)
    }
