package net.shasankp000.PlayerUtils;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Collision-based standing checks for fake players whose onGround flag can lag. */
public final class SurvivalGrounding {
    private SurvivalGrounding() {
    }

    public static Optional<BlockPos> resolveSupportBlock(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        var box = player.getBoundingBox();
        int minX = BlockPos.containing(box.minX + 1.0E-4D, 0.0D, 0.0D).getX();
        int maxX = BlockPos.containing(box.maxX - 1.0E-4D, 0.0D, 0.0D).getX();
        int minZ = BlockPos.containing(0.0D, 0.0D, box.minZ + 1.0E-4D).getZ();
        int maxZ = BlockPos.containing(0.0D, 0.0D, box.maxZ - 1.0E-4D).getZ();
        int floorY = (int) Math.floor(box.minY - 1.0E-4D);

        for (int y = floorY; y >= floorY - 1; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (isSolidDrySupport(world, candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isStandableFeet(ServerLevel world, BlockPos feet) {
        BlockState floor = world.getBlockState(feet.below());
        BlockState body = world.getBlockState(feet);
        BlockState head = world.getBlockState(feet.above());
        return isSolidDrySupport(world, feet.below())
                && isPassable(world, feet, body)
                && isPassable(world, feet.above(), head);
    }

    public static boolean isSolidDrySupport(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.getCollisionShape(world, pos).isEmpty();
    }

    public static boolean isPassable(ServerLevel world, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty()
                && (state.isAir() || state.canBeReplaced() || state.getCollisionShape(world, pos).isEmpty());
    }
}
