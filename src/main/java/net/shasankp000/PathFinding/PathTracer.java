package net.shasankp000.PathFinding;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

public class PathTracer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final double WALKING_SPEED = 4.317; // blocks per second
    private static final double SPRINTING_SPEED = 5.612; // blocks per second
    private static final long MOVEMENT_POLL_INTERVAL_MS = 50L;
    private static final long MIN_SEGMENT_TIMEOUT_MS = 750L;
    private static Queue<Segment> segmentQueue = new LinkedList<>();
    private static boolean shouldSprint;
    private static final int MAX_RETRIES = 5; // Reduced from 10
    private static final AtomicLong pathGeneration = new AtomicLong(0);

    public static class BotSegmentManager {
        private static final Queue<Segment> jobQueue = new LinkedList<>();
        private final MinecraftServer server;
        private static CommandSourceStack botSource = null;
        private final String botName;
        private final long generation;
        private int retries = 0;
        private static boolean isMoving = false;
        private static Segment currentSegment = null; // Track current segment

        // ✅ Add completion tracking
        private static CompletableFuture<String> pathCompletionFuture = null;
        private static final AtomicReference<String> finalResult = new AtomicReference<>("");

        public static boolean getBotMovementStatus() {
            return isMoving;
        }

        public BotSegmentManager(MinecraftServer server, CommandSourceStack botSource, String botName) {
            this(server, botSource, botName, pathGeneration.incrementAndGet());
            jobQueue.clear();
            currentSegment = null;
            isMoving = false;
        }

        private BotSegmentManager(MinecraftServer server, CommandSourceStack botSource, String botName, long generation) {
            this.server = server;
            BotSegmentManager.botSource = botSource;
            this.botName = botName;
            this.generation = generation;
        }

        public static void clearJobs() {
            pathGeneration.incrementAndGet();
            jobQueue.clear();
            isMoving = false;
            currentSegment = null;

            // ✅ Reset completion tracking
            if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                pathCompletionFuture.complete("Path cleared");
            }
            pathCompletionFuture = null;
            finalResult.set("");

            LOGGER.info("Job queue flushed.");
        }

        // ✅ Add method to get completion future
        public static CompletableFuture<String> getPathCompletionFuture() {
            if (pathCompletionFuture == null) {
                pathCompletionFuture = new CompletableFuture<>();
            }
            return pathCompletionFuture;
        }

        public void addSegmentJob(Segment segment) {
            jobQueue.add(segment);
        }

        public void startProcessing() {
            if (!isCurrentGeneration()) {
                LOGGER.info("Ignoring stale path processing for {} generation {}", botName, generation);
                return;
            }

            // ✅ Initialize completion future if not already done
            if (pathCompletionFuture == null) {
                pathCompletionFuture = new CompletableFuture<>();
            }

            if (!jobQueue.isEmpty()) {
                currentSegment = jobQueue.poll();
                executeSegment(currentSegment);
            } else {
                isMoving = false;
                currentSegment = null;

                // if was sprinting previously, set to false.
                if (shouldSprint) {
                    shouldSprint = false; // reset the flag
                    runPlayerCommand("unsprint");
                }

                // ✅ Complete the path and set final result
                String result = tracePathOutput(botSource);
                finalResult.set(result);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(result);
                }

                LOGGER.info("No more segments to process. Final result: {}", result);
            }
        }

        public static Queue<Segment> getJobQueue() {
            return jobQueue;
        }

        private void executeSegment(Segment segment) {
            if (!isCurrentGeneration()) {
                LOGGER.info("Ignoring stale segment for {} generation {}", botName, generation);
                return;
            }

            LOGGER.info("START segment: " + segment);
            updateFacing(segment);

            int distance = calculateAxisAlignedDistance(segment.start(), segment.end());

            if (distance == 0) {
                LOGGER.info("Skipping zero-length segment: {}", segment);
                waitForSegmentCompletion(segment); // instantly mark as complete
                return;
            }

            if (isVerticalOnlySegment(segment)) {
                executeVerticalSegment(segment);
                return;
            }

            boolean shortJumpSegment = segment.jump() && distance <= 2;
            if (segment.sprint() && !shortJumpSegment) {
                runPlayerCommand("sprint");
            }
            else {
                // if was set to sprint before, stop sprinting anyways.
                runPlayerCommand("unsprint");
            }

            double speed = segment.sprint() && !shortJumpSegment ? SPRINTING_SPEED : WALKING_SPEED;
            long timeoutMillis = calculateSegmentTimeoutMillis(distance, speed);

            LOGGER.info("Walking toward {} for up to {} ms", segment.end(), timeoutMillis);
            System.out.println("Walking for up to " + (timeoutMillis / 1000.0) + " seconds");

            runPlayerCommand("move forward");

            isMoving = true;

            if (segment.jump()) {
                scheduler.schedule(() -> {
                    if (!isCurrentGeneration()) {
                        return;
                    }
                    runPlayerCommand("jump");
                    LOGGER.info("{} performed a jump!", botName);
                }, Math.min(250L, timeoutMillis / 2L), TimeUnit.MILLISECONDS);
            }

            pollUntilSegmentReached(segment, System.currentTimeMillis() + timeoutMillis);
        }

        private boolean isVerticalOnlySegment(Segment segment) {
            return segment.start().getX() == segment.end().getX()
                    && segment.start().getZ() == segment.end().getZ()
                    && segment.start().getY() != segment.end().getY();
        }

        private void executeVerticalSegment(Segment segment) {
            LOGGER.info("Strict survival mode cannot execute vertical-only segment by teleport: {}", segment);
            isMoving = true;
            runPlayerCommand("unsprint");
            stopMovement();

            scheduler.schedule(() -> {
                if (!isCurrentGeneration()) {
                    return;
                }

                waitForSegmentCompletion(segment);
            }, 50, TimeUnit.MILLISECONDS);
        }

        private long calculateSegmentTimeoutMillis(int distance, double speed) {
            long expectedMillis = (long) Math.ceil((distance / speed) * 1000.0);
            return Math.max(MIN_SEGMENT_TIMEOUT_MS, expectedMillis + 750L);
        }

        private void pollUntilSegmentReached(Segment segment, long deadlineMs) {
            scheduler.schedule(() -> {
                if (!isCurrentGeneration()) {
                    return;
                }

                ServerPlayer player = botSource.getPlayer();
                if (player == null) {
                    stopMovement();
                    waitForSegmentCompletion(segment);
                    return;
                }

                if (hasReachedTarget(player.blockPosition(), player.getX(), player.getY(), player.getZ(), segment.end(), segment.jump())) {
                    stopMovement();
                    LOGGER.info("{} has stopped walking after reaching {}", botName, segment.end());
                    waitForSegmentCompletion(segment);
                    return;
                }

                if (System.currentTimeMillis() >= deadlineMs) {
                    stopMovement();
                    LOGGER.warn("{} timed out walking toward {}; current position is {}", botName, segment.end(), player.blockPosition());
                    waitForSegmentCompletion(segment);
                    return;
                }

                pollUntilSegmentReached(segment, deadlineMs);
            }, MOVEMENT_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        private void waitForSegmentCompletion(Segment completedSegment) {
            if (!isCurrentGeneration()) {
                LOGGER.info("Ignoring stale segment completion for {} generation {}", botName, generation);
                return;
            }

            ServerPlayer player = botSource.getPlayer();
            if (player == null) {
                LOGGER.error("Player is null, cannot continue pathfinding");
                // ✅ Complete with error
                String errorResult = "Player not found";
                finalResult.set(errorResult);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(errorResult);
                }
                return;
            }

            BlockPos currentPos = player.blockPosition();

            // Get the final destination for distance checking
            BlockPos finalDestination = getFinalDestination();
            if (finalDestination == null) {
                finalDestination = completedSegment.end();
            }

            LOGGER.info("Bot at: {}, Target: {}, Final: {}", currentPos, completedSegment.end(), finalDestination);

            // Check if we've reached the segment target with improved tolerance
            if (hasReachedTarget(player.blockPosition(), player.getX(), player.getY(), player.getZ(), completedSegment.end(), completedSegment.jump())) {
                LOGGER.info("✅ Reached segment target: {}", completedSegment.end());
                retries = 0;
                isMoving = false;
                startProcessing(); // This will either start next segment or complete the path
                return;
            }

            // Check if we're close to the final destination and can stop
            if (isCloseToFinalDestination(currentPos, finalDestination)) {
                LOGGER.info("✅ Bot is close enough to final destination: {}", finalDestination);
                flushAllMovementTasks();
                isMoving = false;

                // ✅ Complete with success
                String result = tracePathOutput(botSource);
                finalResult.set(result);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(result);
                }
                return;
            }

            // Try to find if we're already at a future segment position
            if (tryAdvancedSegmentSkip(currentPos)) {
                return;
            }

            retries++;
            LOGGER.warn("Segment not reached. Retry {}/{}", retries, MAX_RETRIES);

            // If we haven't exceeded retries, try to re-path
            if (retries < MAX_RETRIES) {
                LOGGER.info("Attempting re-pathfinding from {} to {}", currentPos, finalDestination);

                ServerLevel world = botSource.getServer().overworld();
                List<PathFinder.PathNode> newPath = PathFinder.calculatePath(currentPos, finalDestination, world);

                if (newPath.isEmpty()) {
                    LOGGER.error("Re-pathfinding failed! Stopping bot.");
                    flushAllMovementTasks();
                    isMoving = false;

                    // ✅ Complete with failure
                    String failureResult = "Re-pathfinding failed";
                    finalResult.set(failureResult);
                    if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                        pathCompletionFuture.complete(failureResult);
                    }
                    return;
                }

                // Replace pending segments without completing the public path future.
                jobQueue.clear();
                currentSegment = null;
                segmentQueue.clear();

                List<PathFinder.PathNode> simplified = PathFinder.simplifyPath(newPath, world);
                Queue<Segment> newSegments = PathFinder.convertPathToSegments(simplified, shouldSprint);

                LOGGER.info("New path generated with {} segments", newSegments.size());
                if (newSegments.isEmpty()) {
                    if (GoTo.hasArrivedAtTarget(currentPos, finalDestination)) {
                        String result = tracePathOutput(botSource);
                        finalResult.set(result);
                        if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                            pathCompletionFuture.complete(result);
                        }
                    } else {
                        LOGGER.warn("Re-pathing produced no segments but bot is at {} instead of {}", currentPos, finalDestination);
                        String failureResult = "Re-pathing failed";
                        finalResult.set(failureResult);
                        if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                            pathCompletionFuture.complete(failureResult);
                        }
                    }
                    isMoving = false;
                    return;
                }
                segmentQueue = new LinkedList<>(newSegments);
                newSegments.forEach(this::addSegmentJob);

                retries = 0; // Reset retries for new path
                startProcessing();
            } else {
                LOGGER.warn("Max retries exceeded. Stopping pathfinding.");
                flushAllMovementTasks();
                isMoving = false;

                // ✅ Complete with retry failure
                String retryFailureResult = "Max retries exceeded";
                finalResult.set(retryFailureResult);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(retryFailureResult);
                }
            }
        }

        private BlockPos getFinalDestination() {
            if (segmentQueue.isEmpty()) {
                return currentSegment != null ? currentSegment.end() : null;
            }

            // Get the last segment's end position
            Segment lastSegment = ((LinkedList<Segment>) segmentQueue).peekLast();
            return lastSegment != null ? lastSegment.end() : (currentSegment != null ? currentSegment.end() : null);
        }

        private boolean isCloseToFinalDestination(BlockPos currentPos, BlockPos finalDestination) {
            if (finalDestination == null) return false;

            double distance = Math.sqrt(currentPos.distSqr(finalDestination));
            return distance <= 0.75; // Coordinate navigation should not stop while still multiple blocks away.
        }

        private boolean tryAdvancedSegmentSkip(BlockPos currentPos) {
            // Check if current position matches any upcoming segment start/end
            List<Segment> remainingSegments = new ArrayList<>(jobQueue);

            for (int i = 0; i < remainingSegments.size(); i++) {
                Segment segment = remainingSegments.get(i);

                // Only skip forward when the bot has actually reached an upcoming
                // segment end. Matching segment starts is too eager: standing near
                // the beginning of a path can otherwise skip the whole route.
                if (isAdvancedSegmentEndMatch(currentPos, segment.end())) {
                    LOGGER.info("✅ Bot advanced to segment {}: {}", i, segment);

                    // Clear old segments up to this point without completing the public path future.
                    // clearJobs() reports "Path cleared", which makes callers think navigation is done
                    // while the tracer is still processing the remaining path.
                    jobQueue.clear();
                    currentSegment = null;

                    // Add remaining segments starting from this one
                    for (int j = i; j < remainingSegments.size(); j++) {
                        addSegmentJob(remainingSegments.get(j));
                    }

                    retries = 0;
                    startProcessing();
                    return true;
                }
            }
            return false;
        }

        private boolean isAdvancedSegmentEndMatch(BlockPos currentPos, BlockPos segmentEnd) {
            return currentPos.getX() == segmentEnd.getX()
                    && currentPos.getZ() == segmentEnd.getZ()
                    && Math.abs(currentPos.getY() - segmentEnd.getY()) <= 1;
        }

        private boolean isCurrentGeneration() {
            return generation == pathGeneration.get();
        }

        // ✅ Updated to return proper format for parsing
        public static String tracePathOutput(CommandSourceStack botSource) {
            if (botSource == null || botSource.getPlayer() == null) {
                return "Bot not found";
            }

            ServerPlayer bot = botSource.getPlayer();
            BlockPos currentPos = bot.blockPosition();

            // Return in the format expected by parseOutputValues
            return String.format("Bot moved to position - x: %d y: %d z: %d",
                    currentPos.getX(), currentPos.getY(), currentPos.getZ());
        }

        // Improved target reaching detection
        static boolean hasReachedTarget(BlockPos current, double playerX, double playerY, double playerZ, BlockPos target, boolean jump) {
            boolean sameHorizontalBlock = current.getX() == target.getX() && current.getZ() == target.getZ();
            int allowedYDifference = jump ? 2 : 1;
            boolean acceptableY = Math.abs(current.getY() - target.getY()) <= allowedYDifference;
            if (!sameHorizontalBlock || !acceptableY) {
                return false;
            }

            double dx = Math.abs(playerX - (target.getX() + 0.5));
            double dy = Math.abs(playerY - target.getY());
            double dz = Math.abs(playerZ - (target.getZ() + 0.5));
            double horizontalTolerance = jump ? 1.25 : 1.0;
            double verticalTolerance = jump ? 2.0 : 1.25;

            boolean reached = dx <= horizontalTolerance && dz <= horizontalTolerance && dy <= verticalTolerance;
            if (reached) {
                LOGGER.info("Target reached! dx={}, dy={}, dz={} (tolerance: h={}, v={})",
                        String.format("%.2f", dx),
                        String.format("%.2f", dy),
                        String.format("%.2f", dz),
                        horizontalTolerance,
                        verticalTolerance);
            }
            return reached;
        }

        // Update calculateAxisAlignedDistance for precision
        private int calculateAxisAlignedDistance(BlockPos current, BlockPos target) {
            ServerPlayer player = botSource.getPlayer();
            if (player != null) {
                double dx = Math.abs(player.getX() - (target.getX() + 0.5));
                double dy = Math.abs(player.getY() - target.getY());
                double dz = Math.abs(player.getZ() - (target.getZ() + 0.5));
                return (int) Math.max(1, Math.round(dx + dy + dz));
            }
            return Math.abs(current.getX() - target.getX()) + Math.abs(current.getY() - target.getY()) + Math.abs(current.getZ() - target.getZ());
        }

        private String lastDirection = "north"; // initialize with something reasonable

        private void updateFacing(Segment segment) {
            BlockPos start = segment.start();
            BlockPos end = segment.end();

            int dx = end.getX() - start.getX();
            int dz = end.getZ() - start.getZ();
            int dy = end.getY() - start.getY();

            String direction = null;

            if (Math.abs(dx) > 0 && dz == 0) {
                direction = dx > 0 ? "east" : "west";
            } else if (Math.abs(dz) > 0 && dx == 0) {
                direction = dz > 0 ? "south" : "north";
            } else if (Math.abs(dy) > 0 && dx == 0 && dz == 0) {
                direction = dy > 0 ? "up" : "down";
            }

            if (direction == null) {
                direction = lastDirection;
            } else {
                lastDirection = direction;
            }

            runPlayerCommand("look " + direction);
            LOGGER.info("{} is now facing {} (dx: {}, dy: {}, dz: {})", botName, direction, dx, dy, dz);
        }

        private void stopMovement() {
            runPlayerCommand("stop");
        }

        private void runPlayerCommand(String command) {
            String fullCommand = formatPlayerCommand(botName, command);
            Runnable task = () -> {
                LOGGER.info("Executing player command for {}: {}", botName, fullCommand);
                server.getCommands().performPrefixedCommand(botSource, fullCommand);
            };

            if (server.isSameThread()) {
                task.run();
            } else {
                LOGGER.info("Queueing player command for {} on server thread: {}", botName, fullCommand);
                server.execute(task);
            }
        }

        static String formatPlayerCommand(String botName, String command) {
            return "/player " + botName + " " + command;
        }
    }

    // ✅ Updated to return CompletableFuture for proper async handling
    public static CompletableFuture<String> tracePath(MinecraftServer server, CommandSourceStack botSource, String botName, Queue<Segment> segments, boolean sprint) {
        shouldSprint = sprint;
        segmentQueue = new LinkedList<>(segments); // Create a copy

        // Clear any existing completion future
        BotSegmentManager.clearJobs();
        long generation = pathGeneration.get();

        // Create the manager and initialize the completion future FIRST
        BotSegmentManager manager = new BotSegmentManager(server, botSource, botName, generation);
        CompletableFuture<String> completionFuture = BotSegmentManager.getPathCompletionFuture();

        // Start the path execution in a separate thread
        new Thread(() -> {
            try {
                segments.forEach(manager::addSegmentJob);
                manager.startProcessing();
            } catch (Exception e) {
                LOGGER.error("Error starting path processing: ", e);
                if (!completionFuture.isDone()) {
                    completionFuture.complete("Path processing failed: " + e.getMessage());
                }
            }
        }).start();

        return completionFuture;
    }


    public static void flushAllMovementTasks() {
        segmentQueue.clear();
        BotSegmentManager.clearJobs();
        LOGGER.info("All movement tasks flushed");
    }
}
