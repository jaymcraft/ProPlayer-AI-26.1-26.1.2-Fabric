package net.shasankp000.PlayerUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ToolSelector {

    public static ItemStack selectBestToolForBlock(ServerPlayer bot, BlockState blockState) {
        ItemStack bestTool = ItemStack.EMPTY;
        float highestSpeed = 0.0f;

        for (int slot = 0; slot < bot.getInventory().getContainerSize(); slot++) {
            ItemStack item = bot.getInventory().getItem(slot);
            if (item.isEmpty()) continue;
            if (!hasEnoughDurabilityForMining(item)) continue;

            float speed = item.getDestroySpeed(blockState);
            if (speed > highestSpeed) {
                highestSpeed = speed;
                bestTool = item;
            }
        }

        if (highestSpeed <= 1.0f) {
            ItemStack fallbackTool = selectRequiredToolFallback(bot, blockState);
            if (!fallbackTool.isEmpty()) {
                return fallbackTool;
            }
            return ItemStack.EMPTY;
        }

        return bestTool;
    }

    private static ItemStack selectRequiredToolFallback(ServerPlayer bot, BlockState blockState) {
        if (!blockState.requiresCorrectToolForDrops() && !blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return ItemStack.EMPTY;
        }

        for (int slot = 0; slot < bot.getInventory().getContainerSize(); slot++) {
            ItemStack item = bot.getInventory().getItem(slot);
            if (!item.isEmpty() && hasEnoughDurabilityForMining(item) && isPickaxe(item)) {
                return item;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isPickaxe(ItemStack item) {
        if (item.is(ItemTags.PICKAXES)) {
            return true;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        return itemId != null && itemId.getPath().endsWith("_pickaxe");
    }

    private static boolean hasEnoughDurabilityForMining(ItemStack stack) {
        return !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1;
    }
}
