package com.example.villagerworkers.goal;

import com.example.villagerworkers.ToolRole;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Hoe: harvests fully grown crops (wheat, carrots, potatoes, beetroot) and replants them. */
public class HarvestCropGoal extends ToolWorkGoal {

    public HarvestCropGoal(Villager villager) {
        super(villager, ToolRole.FARMER, 0.6);
    }

    private boolean isMatureCrop(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    @Override
    protected List<BlockPos> findCandidates() {
        return scan(10, 2, 2, this::isMatureCrop, 6);
    }

    @Override
    protected boolean isTargetValid() {
        return isMatureCrop(target);
    }

    @Override
    protected int beginWork() {
        return 10;
    }

    @Override
    protected void completeWork() {
        ServerLevel level = level();
        BlockState state = level.getBlockState(target);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return;
        }
        collectDrops(state, target);
        level.levelEvent(2001, target, Block.getId(state)); // break particles + sound
        level.setBlock(target, crop.getStateForAge(0), Block.UPDATE_ALL); // replanting is free
        damageTool(1);
    }
}
