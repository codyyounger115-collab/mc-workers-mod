package com.example.villagerworkers.goal;

import com.example.villagerworkers.ToolRole;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Fishing rod: walks to nearby water, waits, then pulls up loot from the vanilla fishing table. */
public class FishGoal extends ToolWorkGoal {

    public FishGoal(Villager villager) {
        super(villager, ToolRole.FISHER, 0.6);
    }

    private boolean isFishableWater(BlockPos pos) {
        ServerLevel level = level();
        return level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos).isSource()
                && level.getFluidState(pos.below()).is(FluidTags.WATER) // at least 2 deep
                && level.getBlockState(pos.above()).isAir();
    }

    @Override
    protected List<BlockPos> findCandidates() {
        return scan(12, 3, 3, this::isFishableWater, 6);
    }

    @Override
    protected boolean isTargetValid() {
        return isFishableWater(target);
    }

    @Override
    protected int pathAccuracy() {
        return 3; // standing within 3 blocks of the water is fine
    }

    @Override
    protected double reachDistanceSqr() {
        return 4.5 * 4.5;
    }

    @Override
    protected int beginWork() {
        return 300 + villager.getRandom().nextInt(300); // 15-30 seconds per bite
    }

    @Override
    protected void onWorkTick(int ticks, int duration) {
        ServerLevel level = level();
        double x = target.getX() + 0.5;
        double y = target.getY() + 1.0;
        double z = target.getZ() + 0.5;
        if (ticks % 8 == 0) {
            level.sendParticles(ParticleTypes.FISHING, x, y, z, 2, 0.2, 0.0, 0.2, 0.05);
        }
        if (ticks == duration - 10) {
            level.sendParticles(ParticleTypes.SPLASH, x, y, z, 10, 0.2, 0.1, 0.2, 0.0);
            level.playSound(null, target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
        }
    }

    @Override
    protected void completeWork() {
        ServerLevel level = level();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.TOOL, tool())
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        for (ItemStack catchItem : table.getRandomItems(params)) {
            collect(catchItem);
        }
        damageTool(1);
    }
}
