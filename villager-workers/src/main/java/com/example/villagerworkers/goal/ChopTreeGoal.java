package com.example.villagerworkers.goal;

import com.example.villagerworkers.ToolRole;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Axe: walks to the base of a natural tree and fells the whole thing.
 * A "natural tree" is a connected group of logs that touches non-persistent leaves, so
 * player-built log cabins are left alone.
 */
public class ChopTreeGoal extends ToolWorkGoal {
    private static final int MAX_LOGS = 128;

    private int lastStage = -1;

    public ChopTreeGoal(Villager villager) {
        super(villager, ToolRole.LUMBERJACK, 0.6);
    }

    private boolean isTreeBase(BlockPos pos) {
        ServerLevel level = level();
        return level.getBlockState(pos).is(BlockTags.LOGS)
                && !level.getBlockState(pos.below()).is(BlockTags.LOGS);
    }

    private boolean hasNaturalLeaves(BlockPos pos) {
        ServerLevel level = level();
        for (Direction dir : Direction.values()) {
            BlockState state = level.getBlockState(pos.relative(dir));
            if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    /** All logs connected to {@code start}, or an empty list if it is not a natural tree. */
    private List<BlockPos> collectTree(BlockPos start) {
        ServerLevel level = level();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        boolean natural = false;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!natural && hasNaturalLeaves(current)) {
                natural = true;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = current.offset(dx, dy, dz);
                        if (seen.contains(next) || !level.getBlockState(next).is(BlockTags.LOGS)) continue;
                        if (seen.size() >= MAX_LOGS) {
                            return List.of(); // too big, probably a building
                        }
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return natural ? new ArrayList<>(seen) : List.of();
    }

    @Override
    protected List<BlockPos> findCandidates() {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos base : scan(12, 3, 4, this::isTreeBase, 12)) {
            if (!collectTree(base).isEmpty()) {
                valid.add(base);
                if (valid.size() >= 4) break;
            }
        }
        return valid;
    }

    @Override
    protected boolean isTargetValid() {
        return level().getBlockState(target).is(BlockTags.LOGS);
    }

    @Override
    protected int beginWork() {
        lastStage = -1;
        ServerLevel level = level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        float speed = tool().getDestroySpeed(state);
        return speed <= 0 ? 40 : Math.max(10, (int) (hardness * 30.0F / speed * 2.0F));
    }

    @Override
    protected void onWorkTick(int ticks, int duration) {
        int stage = Math.min(9, ticks * 10 / duration);
        if (stage != lastStage) {
            lastStage = stage;
            level().destroyBlockProgress(villager.getId(), target, stage);
        }
    }

    @Override
    protected void completeWork() {
        ServerLevel level = level();
        level.destroyBlockProgress(villager.getId(), target, -1);
        for (BlockPos pos : collectTree(target)) {
            if (!level.getBlockState(pos).is(BlockTags.LOGS)) continue;
            breakBlock(pos);
            if (damageTool(1)) {
                break; // axe broke mid-tree
            }
        }
    }

    @Override
    protected void onStop() {
        if (target != null) {
            level().destroyBlockProgress(villager.getId(), target, -1);
        }
    }
}
