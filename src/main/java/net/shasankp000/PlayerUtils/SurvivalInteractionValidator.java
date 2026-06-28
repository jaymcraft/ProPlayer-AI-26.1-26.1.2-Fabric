package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One authoritative reach/visibility check for bot actions.  Every block
 * interaction is measured from the player's eye to an actually exposed face,
 * rather than from the player's feet to a block centre.
 */
public final class SurvivalInteractionValidator {
    private static final double VANILLA_SURVIVAL_BLOCK_REACH = 4.5D;
    private static final double VANILLA_SURVIVAL_ENTITY_REACH = 3.0D;

    private SurvivalInteractionValidator() {
    }

    public static double blockReach(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        return attribute == null ? VANILLA_SURVIVAL_BLOCK_REACH : attribute.getValue();
    }

    public static double entityReach(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        return attribute == null ? VANILLA_SURVIVAL_ENTITY_REACH : attribute.getValue();
    }

    public static boolean canReachVisibleBlock(ServerPlayer player, BlockPos target) {
        for (Direction face : Direction.values()) {
            if (canReachVisibleFace(player, target, face)) {
                return true;
            }
        }
        return false;
    }

    /** Range-only check for planning a placement into an empty block space. */
    public static boolean isWithinBlockReach(ServerPlayer player, BlockPos target) {
        Vec3 eye = player.getEyePosition(1.0F);
        for (Direction face : Direction.values()) {
            if (eye.distanceToSqr(faceCenter(target, face)) <= square(blockReach(player))) {
                return true;
            }
        }
        return false;
    }

    public static boolean canReachVisibleFace(ServerPlayer player, BlockPos target, Direction face) {
        Vec3 hit = faceCenter(target, face);
        if (player.getEyePosition(1.0F).distanceToSqr(hit) > square(blockReach(player))) {
            return false;
        }

        Vec3 insideBlock = hit.add(face.getStepX() * -0.01D, face.getStepY() * -0.01D, face.getStepZ() * -0.01D);
        BlockHitResult result = player.level().clip(new ClipContext(
                player.getEyePosition(1.0F), insideBlock,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(target);
    }

    public static Vec3 faceCenter(BlockPos target, Direction face) {
        return Vec3.atCenterOf(target).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D);
    }

    public static boolean canReachVisibleEntity(ServerPlayer player, Entity target) {
        if (target == null || target.isRemoved() || !player.hasLineOfSight(target)) {
            return false;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        var box = target.getBoundingBox();
        double x = clamp(eye.x, box.minX, box.maxX);
        double y = clamp(eye.y, box.minY, box.maxY);
        double z = clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceToSqr(x, y, z) <= square(entityReach(player));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
