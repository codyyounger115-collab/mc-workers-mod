package com.example.villagerworkers.goal;

import com.example.villagerworkers.ToolRole;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;

/** Pickaxe: mines exposed ore the pickaxe is strong enough for. */
public class MineOreGoal extends ToolWorkGoal {
    private int lastStage = -1;

    public MineOreGoal(Villager villager) {
        super(villager, ToolRole.MINER, 0.6);
    }

    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES);
    }

    private boolean isMineable(BlockPos pos) {
        ServerLevel level = level();
        BlockState state = level.getBlockState(pos);
        if (!isOre(state) || !tool().isCorrectToolForDrops(state)) {
            return false;
        }
        // Only ore with at least one open side; the villager cannot tunnel.
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            if (level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<BlockPos> findCandidates() {
        return scan(10, 5, 5, this::isMineable, 4);
    }

    @Override
    protected boolean isTargetValid() {
        return isOre(level().getBlockState(target));
    }

    @Override
    protected int beginWork() {
        lastStage = -1;
        ServerLevel level = level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        float speed = tool().getDestroySpeed(state);
        // Roughly twice as slow as a player with the same tool.
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
        level().destroyBlockProgress(villager.getId(), target, -1);
        if (isTargetValid()) {
            breakBlock(target);
            damageTool(1);
        }
    }

    @Override
    protected void onStop() {
        if (target != null) {
            level().destroyBlockProgress(villager.getId(), target, -1);
        }
    }
}
