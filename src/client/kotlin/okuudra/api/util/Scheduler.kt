package okuudra.api.util

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.util.concurrent.ConcurrentLinkedQueue

object Scheduler {
    private class Task(var ticks: Int, val action: () -> Unit)

    private val queued = ConcurrentLinkedQueue<() -> Unit>()
    private val delayed = ArrayList<Task>()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            while (true) (queued.poll() ?: break)()
            val due = delayed.filter { --it.ticks <= 0 }
            delayed.removeAll(due)
            due.forEach { runCatching(it.action) }
        }
    }

    fun onMainThread(action: () -> Unit) {
        queued.add(action)
    }

    fun later(ticks: Int, action: () -> Unit) {
        onMainThread { delayed.add(Task(ticks, action)) }
    }
}
