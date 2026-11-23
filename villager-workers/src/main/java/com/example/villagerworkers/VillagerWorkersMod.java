package com.example.villagerworkers;

import com.example.villagerworkers.goal.ChopTreeGoal;
import com.example.villagerworkers.goal.FishGoal;
import com.example.villagerworkers.goal.HarvestCropGoal;
import com.example.villagerworkers.goal.MineOreGoal;
import com.example.villagerworkers.goal.ToolWorkGoal;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.Villager;

public class VillagerWorkersMod implements ModInitializer {
    public static final String MOD_ID = "villagerworkers";

    @Override
    public void onInitialize() {
        VillagerStorage.init();
        VillagerToolInteraction.register();

        // A villager that dies drops whatever it was carrying to its chest.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Villager villager) {
                VillagerStorage.dropHaul(villager);
            }
        });

        // Goals are not saved with the entity, so (re)attach them whenever a villager loads.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Villager villager) {
                addWorkGoals(villager);
            }
        });
    }

    private static void addWorkGoals(Villager villager) {
        GoalSelector selector = villager.goalSelector; // public via the access widener
        boolean alreadyAdded = selector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof ToolWorkGoal);
        if (alreadyAdded) {
            return;
        }
        selector.addGoal(3, new MineOreGoal(villager));
        selector.addGoal(3, new ChopTreeGoal(villager));
        selector.addGoal(3, new HarvestCropGoal(villager));
        selector.addGoal(3, new FishGoal(villager));
    }
}
