package okuudra.features.impl.misc.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import okuudra.api.util.Chat
import okuudra.api.util.Scheduler
import okuudra.gui.ConfigScreen
import okuudra.gui.HudEditScreen

private typealias Source = FabricClientCommandSource

object Commands {
    private val tiers = listOf("kuudra_normal", "kuudra_hot", "kuudra_burning", "kuudra_fiery", "kuudra_infernal")

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            for (name in listOf("okuudra", "ok")) {
                dispatcher.register(
                    screen(name) { ConfigScreen() }
                        .then(screen("hud") { HudEditScreen() }),
                )
            }
            tiers.forEachIndexed { index, id ->
                dispatcher.register(ClientCommands.literal("t${index + 1}").executes {
                    Chat.command("joindungeon $id")
                    1
                })
            }
        }
    }

    private fun screen(name: String, open: () -> Screen): LiteralArgumentBuilder<Source> = ClientCommands.literal(name).executes {
        Scheduler.onMainThread { Minecraft.getInstance().setScreen(open()) }
        1
    }
}
