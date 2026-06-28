package net.shasankp000.PlayerUtils;

import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.Entity.LookController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiningTool {

    private static final long MINING_TICK_MS = 50;
    private static final double MAX_MINING_DISTANCE = 5.0;
    private static final int MAX_LEAF_OBSTRUCTIONS_TO_CLEAR = 4;
    private static final long LEAF_CLEAR_TIMEOUT_MS = 5000;
    private static final long MAX_BLOCK_BREAK_TIMEOUT_MS = 30000;
    public static final Logger LOGGER = LoggerFactory.getLogger("mining-tool");

    public static CompletableFuture<String> mineBlock(ServerPlayer bot, BlockPos targetBlockPos) {
        CompletableFuture<String> miningResult = new CompletableFuture<>();
        ExecutorService miningExecutor = Executors.newSingleThreadExecutor();
        miningExecutor.submit(() -> {
            try {
                clearLeafObstructions(bot, targetBlockPos);
                mineVisibleBlockSurvival(bot, targetBlockPos, MAX_BLOCK_BREAK_TIMEOUT_MS, "Mining complete!", miningResult);
            }
            catch (Exception e) {
                LOGGER.error("Error in mining tool! {}", e.getMessage());
                miningResult.complete("⚠️ Failed to mine block: " + e.getMessage());
            } finally {
                miningExecutor.shutdownNow();
            }
        });

        return miningResult;
    }

    private static void clearLeafObstructions(ServerPlayer bot, BlockPos targetBlockPos) {
        for (int attempts = 0; attempts < MAX_LEAF_OBSTRUCTIONS_TO_CLEAR; attempts++) {
            if (canReachVisibleBlock(bot, targetBlockPos)) {
                return;
            }

            BlockPos leafPos = findLeafObstruction(bot, targetBlockPos);
            if (leafPos == null) {
                return;
            }

            LOGGER.info("Clearing leaf obstruction {} before mining {}", leafPos, targetBlockPos);
            boolean cleared = mineObstruction(bot, leafPos);
            if (!cleared) {
                LOGGER.warn("Could not clear leaf obstruction {} before timeout", leafPos);
                return;
            }
            LookController.faceBlock(bot, targetBlockPos);
        }
    }

    private static BlockPos findLeafObstruction(ServerPlayer bot, BlockPos targetBlockPos) {
        Level world = bot.level();
        for (Direction direction : Direction.values()) {
            Vec3 faceCenter = Vec3.atCenterOf(targetBlockPos).add(
                    direction.getStepX() * 0.5,
                    direction.getStepY() * 0.5,
                    direction.getStepZ() * 0.5
            );
            Vec3 endInsideTarget = faceCenter.add(
                    direction.getStepX() * -0.01,
                    direction.getStepY() * -0.01,
                    direction.getStepZ() * -0.01
            );
            BlockHitResult lineOfSight = world.clip(new ClipContext(
                    bot.getEyePosition(1.0F),
                    endInsideTarget,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    bot
            ));

            if (lineOfSight.getType() != HitResult.Type.BLOCK) {
                continue;
            }

            BlockPos hitPos = lineOfSight.getBlockPos();
            if (hitPos.equals(targetBlockPos)) {
                return null;
            }

            BlockState hitState = world.getBlockState(hitPos);
            if (hitState.is(BlockTags.LEAVES) && Math.sqrt(hitPos.distToCenterSqr(bot.position())) <= MAX_MINING_DISTANCE) {
                return hitPos;
            }
        }

        return null;
    }

    private static boolean mineObstruction(ServerPlayer bot, BlockPos obstructionPos) {
        try {
            return mineVisibleBlockSurvival(bot, obstructionPos, LEAF_CLEAR_TIMEOUT_MS, "Leaf obstruction cleared", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.warn("Failed to clear leaf obstruction {}: {}", obstructionPos, e.getMessage());
            return false;
        }
    }

    private static boolean mineVisibleBlockSurvival(
            ServerPlayer bot,
            BlockPos targetBlockPos,
            long timeoutMs,
            String successMessage,
            CompletableFuture<String> resultFuture
    ) throws InterruptedException {
        LookController.faceBlock(bot, targetBlockPos);

        BlockHitResult visibleHit = getVisibleHitResult(bot, targetBlockPos);
        if (visibleHit == null) {
            completeIfPresent(resultFuture, "❌ Cannot mine through blocks at " + targetBlockPos);
            return false;
        }

        BlockState blockState = bot.level().getBlockState(targetBlockPos);
        if (blockState.isAir()) {
            completeIfPresent(resultFuture, successMessage);
            return true;
        }

        ItemStack bestTool = ToolSelector.selectBestToolForBlock(bot, blockState);
        if (bestTool.isEmpty()) {
            if (blockState.requiresCorrectToolForDrops()) {
                completeIfPresent(resultFuture, "❌ No usable tool for "
                        + blockState.getBlock().getName().getString() + " at " + targetBlockPos);
                return false;
            }
            bestTool = bot.getMainHandItem();
        }
        switchToTool(bot, bestTool);

        float destroyProgressPerTick = blockState.getDestroyProgress(bot, bot.level(), targetBlockPos);
        if (destroyProgressPerTick <= 0.0F) {
            completeIfPresent(resultFuture, "❌ Block cannot be mined in survival at " + targetBlockPos);
            return false;
        }

        long estimatedBreakMs = (long) Math.ceil((1.0F / destroyProgressPerTick) * MINING_TICK_MS);
        long deadline = System.currentTimeMillis() + Math.min(timeoutMs, Math.max(estimatedBreakMs + 1500L, 1000L));
        int sequence = 0;

        bot.gameMode.handleBlockBreakAction(
                targetBlockPos,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                visibleHit.getDirection(),
                bot.level().getMaxY(),
                sequence++
        );

        boolean sentStop = false;
        try {
            while (System.currentTimeMillis() <= deadline) {
                BlockState currentState = bot.level().getBlockState(targetBlockPos);
                if (currentState.isAir()) {
                    completeIfPresent(resultFuture, successMessage);
                    return true;
                }

                visibleHit = getVisibleHitResult(bot, targetBlockPos);
                if (visibleHit == null) {
                    completeIfPresent(resultFuture, "❌ Cannot mine through blocks at " + targetBlockPos);
                    return false;
                }

                bot.swing(bot.getUsedItemHand());
                if (!sentStop && System.currentTimeMillis() + MINING_TICK_MS >= deadline - 500L) {
                    bot.gameMode.handleBlockBreakAction(
                            targetBlockPos,
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            visibleHit.getDirection(),
                            bot.level().getMaxY(),
                            sequence++
                    );
                    sentStop = true;
                }
                Thread.sleep(MINING_TICK_MS);
            }

            if (bot.level().getBlockState(targetBlockPos).isAir()) {
                completeIfPresent(resultFuture, successMessage);
                return true;
            }

            completeIfPresent(resultFuture, "⚠️ Mining timed out before the block broke at " + targetBlockPos);
            return false;
        } finally {
            if (!bot.level().getBlockState(targetBlockPos).isAir()) {
                bot.gameMode.handleBlockBreakAction(
                        targetBlockPos,
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                        visibleHit != null ? visibleHit.getDirection() : Direction.UP,
                        bot.level().getMaxY(),
                        sequence
                );
            }
        }
    }

    private static void completeIfPresent(CompletableFuture<String> future, String result) {
        if (future != null && !future.isDone()) {
            future.complete(result);
        }
    }

    private static boolean canReachVisibleBlock(ServerPlayer bot, BlockPos targetBlockPos) {
        return getVisibleHitResult(bot, targetBlockPos) != null;
    }

    private static BlockHitResult getVisibleHitResult(ServerPlayer bot, BlockPos targetBlockPos) {
        double distance = Math.sqrt(targetBlockPos.distToCenterSqr(bot.position()));
        if (distance > MAX_MINING_DISTANCE) {
            LOGGER.warn("Target block {} is too far to mine: {} blocks", targetBlockPos, distance);
            return null;
        }

        Level world = bot.level();
        BlockState targetState = world.getBlockState(targetBlockPos);
        if (targetState.isAir()) {
            return new BlockHitResult(Vec3.atCenterOf(targetBlockPos), Direction.UP, targetBlockPos, false);
        }

        for (Direction direction : Direction.values()) {
            Vec3 faceCenter = Vec3.atCenterOf(targetBlockPos).add(
                    direction.getStepX() * 0.5,
                    direction.getStepY() * 0.5,
                    direction.getStepZ() * 0.5
            );
            Vec3 endInsideTarget = faceCenter.add(
                    direction.getStepX() * -0.01,
                    direction.getStepY() * -0.01,
                    direction.getStepZ() * -0.01
            );
            BlockHitResult lineOfSight = world.clip(new ClipContext(
                    bot.getEyePosition(1.0F),
                    endInsideTarget,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    bot
            ));
            if (lineOfSight.getType() == HitResult.Type.BLOCK && lineOfSight.getBlockPos().equals(targetBlockPos)) {
                return lineOfSight;
            }
        }

        return null;
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
