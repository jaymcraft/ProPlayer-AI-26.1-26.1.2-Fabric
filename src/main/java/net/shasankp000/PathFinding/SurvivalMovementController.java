package net.shasankp000.PathFinding;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Drives fake-player navigation through vanilla LivingEntity.travel each tick. */
public final class SurvivalMovementController {
    private static final Map<UUID, MovementRequest> ACTIVE_MOVEMENT = new ConcurrentHashMap<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(SurvivalMovementController::tick);
    }

    private SurvivalMovementController() {
    }

    public static void startForward(ServerPlayer bot, boolean sprint) {
        bot.setSprinting(sprint);
        ACTIVE_MOVEMENT.put(bot.getUUID(), new MovementRequest(bot, sprint));
    }

    public static void requestJump(ServerPlayer bot) {
        MovementRequest request = ACTIVE_MOVEMENT.get(bot.getUUID());
        if (request != null) {
            request.jumpRequested = true;
        }
    }

    public static void stop(ServerPlayer bot) {
        ACTIVE_MOVEMENT.remove(bot.getUUID());
        bot.setSprinting(false);
    }

    private static void tick(MinecraftServer server) {
        ACTIVE_MOVEMENT.entrySet().removeIf(entry -> {
            MovementRequest request = entry.getValue();
            ServerPlayer bot = request.bot;
            if (bot.isRemoved() || bot.isDeadOrDying()) {
                return true;
            }
            bot.setSprinting(request.sprint);
            if (request.jumpRequested && bot.onGround()) {
                bot.jumpFromGround();
            }
            request.jumpRequested = false;
            // travel() is the vanilla movement path: it applies collision, fluids,
            // gravity, step height, attributes, fall damage and knockback.
            bot.travel(new Vec3(0.0D, 0.0D, 1.0D));
            return false;
        });
    }

    private static final class MovementRequest {
        private final ServerPlayer bot;
        private final boolean sprint;
        private boolean jumpRequested;

        private MovementRequest(ServerPlayer bot, boolean sprint) {
            this.bot = bot;
            this.sprint = sprint;
        }
    }
}
