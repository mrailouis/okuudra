package okuudra.api.util

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

val ItemStack.customData: CompoundTag
    get() = getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

val ItemStack.skyblockId: String
    get() = customData.getString("id").orElse("")
