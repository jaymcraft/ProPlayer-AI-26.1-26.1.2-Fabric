package net.shasankp000.PathFinding;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.PlayerUtils.SurvivalGrounding;
import net.shasankp000.PathFinding.PathFinder.PathNode;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static net.shasankp000.PathFinding.PathFinder.*;

public class GoTo {

    public static String goTo(CommandSourceStack botSource, int x, int y, int z, boolean sprint) {
        return goTo(botSource, x, y, z, sprint, 60);
    }

    public static String goTo(CommandSourceStack botSource, int x, int y, int z, boolean sprint, int timeoutSeconds) {
        MinecraftServer server = botSource.getServer();
        ServerPlayer bot = botSource.getPlayer();
        String botName = botSource.getTextName();

        if (bot == null) {
            System.out.println("Bot not found!");
            return "Bot not found!";
        }

        ServerLevel world = (ServerLevel) bot.level();
        System.out.println("Found bot: " + botSource.getTextName());

        try {
            BlockPos destination = new BlockPos(x, y, z);
            if (!isStandableFeet(world, destination)) {
                return "❌ Movement failed: destination is not a standable survival position";
            }

            // Calculate the path
            List<PathNode> rawPath = calculatePath(bot.blockPosition(), destination, world);

            // Simplify + filter
            List<PathNode> finalPath = simplifyPath(rawPath, world);
            LOGGER.info("Path output: {}", finalPath);

            Queue<Segment> segments = convertPathToSegments(finalPath, sprint);
            LOGGER.info("Generated segments: {}", segments);

            if (segments.isEmpty() && bot.position().distanceToSqr(Vec3.atBottomCenterOf(destination)) > 0.75D * 0.75D) {
                return "❌ Movement failed: no walkable route to the requested position";
            }

            // ✅ Trace the path and wait for completion
            CompletableFuture<String> pathFuture = PathTracer.tracePath(server, botSource, botName, segments, sprint);


            // Wait for path completion with timeout
            String result = pathFuture.get(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);

            String finalOutput = "";

            if (result.equals("Path cleared")) {
                finalOutput = "⚠️ Movement cancelled before reaching the requested position";
            }
            else if (result.equals("Player not found")){
                finalOutput = "❌ Movement failed: player not found";
            }
            else if (result.equals("Max retries exceeded")) {
                finalOutput = "❌ Movement failed: no walkable path to the requested position";
            }
            else if (result.equals("Re-pathing failed")) {
                finalOutput = "❌ Movement failed: no walkable path to the requested position";
            }
            else if (result.contains("Path processing failed: ")) {
                finalOutput = "❌ Movement failed: path tracer could not process the route";
            }
            else {
                finalOutput = PathTracer.BotSegmentManager.tracePathOutput(botSource);
            }

            System.out.println("Path tracer output: " + result);
            System.out.println("Final path output: " + finalOutput);

            return finalOutput; // Already in proper format from PathTracer

        } catch (TimeoutException e) {
            PathTracer.flushAllMovementTasks();
            server.execute(() -> server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " stop"));
            LOGGER.warn("goTo timed out after {}s while moving {} to ({}, {}, {})", timeoutSeconds, botName, x, y, z);
            return String.format("⚠️ goTo timed out after %ds; bot is at x: %d y: %d z: %d",
                    timeoutSeconds, (int) bot.getX(), (int) bot.getY(), (int) bot.getZ());
        } catch (Exception e) {
            LOGGER.error("Error executing goTo: ", e);
            return "Failed to execute goTo: " + e.getMessage();
        }
    }

    private static boolean isStandableFeet(ServerLevel world, BlockPos feet) {
        return SurvivalGrounding.isStandableFeet(world, feet);
    }
}
