package net.shasankp000.PlayerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.shasankp000.Entity.LookController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiningTool {

    public static final Logger LOGGER = LoggerFactory.getLogger("mining-tool");
    private static final Map<UUID, ActiveMining> ACTIVE_MINING = new ConcurrentHashMap<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(MiningTool::tickActiveMining);
    }

    public static CompletableFuture<String> mineBlock(ServerPlayer bot, BlockPos targetBlockPos) {
        CompletableFuture<String> miningResult = new CompletableFuture<>();
        if (bot.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            miningResult.complete("❌ The bot must be in Survival mode to mine blocks");
            return miningResult;
        }

        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) bot.level()).getServer();
        if (server == null) {
            miningResult.complete("❌ Cannot mine: server is unavailable");
            return miningResult;
        }
        server.execute(() -> beginMining(bot, targetBlockPos, miningResult));

        return miningResult;
    }

    private static void beginMining(ServerPlayer bot, BlockPos target, CompletableFuture<String> future) {
        if (!SurvivalInteractionValidator.canReachVisibleBlock(bot, target)) {
            future.complete("❌ Cannot mine through blocks or outside survival reach at " + target);
            return;
        }
        BlockState state = bot.level().getBlockState(target);
        if (state.isAir()) {
            future.complete("❌ No block to mine at " + target);
            return;
        }
        ItemStack tool = ToolSelector.selectBestToolForBlock(bot, state);
        if (tool.isEmpty() && state.requiresCorrectToolForDrops()) {
            future.complete("❌ No usable tool for " + state.getBlock().getName().getString() + " at " + target);
            return;
        }
        if (!tool.isEmpty()) {
            switchToTool(bot, tool);
        }
        ActiveMining previous = ACTIVE_MINING.put(bot.getUUID(), new ActiveMining(bot, target, future));
        if (previous != null && !previous.result.isDone()) {
            previous.result.complete("⚠️ Mining cancelled by a new mining action");
        }
    }

    private static void tickActiveMining(MinecraftServer server) {
        ACTIVE_MINING.entrySet().removeIf(entry -> {
            ActiveMining mining = entry.getValue();
            ServerPlayer bot = mining.bot;
            if (bot.isRemoved() || bot.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                mining.result.complete("❌ Mining cancelled: bot is no longer in Survival");
                return true;
            }
            BlockState state = bot.level().getBlockState(mining.target);
            if (state.isAir()) {
                mining.result.complete("Mining complete!");
                return true;
            }
            if (!SurvivalInteractionValidator.canReachVisibleBlock(bot, mining.target)) {
                mining.result.complete("❌ Mining cancelled: target is obstructed or outside survival reach");
                return true;
            }
            LookController.faceBlock(bot, mining.target);
            bot.swing(bot.getUsedItemHand());
            mining.progress += state.getDestroyProgress(bot, bot.level(), mining.target);
            if (mining.progress < 1.0F) {
                return false;
            }
            if (!bot.gameMode.destroyBlock(mining.target)) {
                mining.result.complete("❌ Mining failed: vanilla block break was rejected");
                return true;
            }
            mining.result.complete("Mining complete!");
            return true;
        });
    }

    private static final class ActiveMining {
        private final ServerPlayer bot;
        private final BlockPos target;
        private final CompletableFuture<String> result;
        private float progress;

        private ActiveMining(ServerPlayer bot, BlockPos target, CompletableFuture<String> result) {
            this.bot = bot;
            this.target = target;
            this.result = result;
        }
    }

    private static void switchToTool(ServerPlayer bot, ItemStack tool) {
        for (int i = 0; i < 9; i++) {
            if (bot.getInventory().getItem(i) == tool) {
                bot.getInventory().setSelectedSlot(i);
                return;
            }
        }

        for (int i = 9; i < bot.getInventory().getContainerSize(); i++) {
            if (bot.getInventory().getItem(i) != tool) {
                continue;
            }

            int hotbarSlot = 8;
            for (int hotbar = 0; hotbar < 9; hotbar++) {
                if (bot.getInventory().getItem(hotbar).isEmpty()) {
                    hotbarSlot = hotbar;
                    break;
                }
            }

            ItemStack stackToMove = bot.getInventory().getItem(i);
            ItemStack displacedStack = bot.getInventory().getItem(hotbarSlot);
            bot.getInventory().setItem(hotbarSlot, stackToMove.copy());
            bot.getInventory().setItem(i, displacedStack.copy());
            bot.getInventory().setSelectedSlot(hotbarSlot);
            bot.getInventory().setChanged();
            return;
        }
    }

}
