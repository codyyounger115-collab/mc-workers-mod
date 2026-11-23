package com.example.villagerworkers;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;

/** The job a villager does, decided by the tool in its main hand. */
public enum ToolRole {
    MINER("Miner"),
    LUMBERJACK("Lumberjack"),
    FARMER("Farmer"),
    FISHER("Fisher");

    public final String displayName;

    ToolRole(String displayName) {
        this.displayName = displayName;
    }

    /** Returns the role for this item, or null if it is not a supported tool. */
    public static ToolRole of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof PickaxeItem) return MINER;
        if (item instanceof AxeItem) return LUMBERJACK;
        if (item instanceof HoeItem) return FARMER;
        if (item instanceof FishingRodItem) return FISHER;
        return null;
    }
}
