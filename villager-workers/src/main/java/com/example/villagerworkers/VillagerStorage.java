package com.example.villagerworkers;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-villager saved data: which chest it is linked to, and the items it is carrying
 * to that chest. Both are saved with the villager (Fabric data attachments).
 */
public final class VillagerStorage {
    private VillagerStorage() {}

    public static final AttachmentType<BlockPos> CHEST = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(VillagerWorkersMod.MOD_ID, "chest"), BlockPos.CODEC);

    public static final AttachmentType<List<ItemStack>> HAUL = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(VillagerWorkersMod.MOD_ID, "haul"), ItemStack.OPTIONAL_LIST_CODEC);

    /** Call from the mod initializer so the attachment types are registered before worlds load. */
    public static void init() {}

    // ---- chest link ----

    /** The linked chest position, or null. */
    public static BlockPos getChest(Villager villager) {
        return villager.getAttached(CHEST);
    }

    public static void setChest(Villager villager, BlockPos pos) {
        villager.setAttached(CHEST, pos.immutable());
    }

    public static void clearChest(Villager villager) {
        villager.removeAttached(CHEST);
    }

    /** The inventory at a chest/barrel position (double chests are handled), or null. */
    public static Container containerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof BarrelBlockEntity barrel ? barrel : null;
    }

    /** Puts the stack into the container; returns whatever did not fit. */
    public static ItemStack insert(Container container, ItemStack stack) {
        return HopperBlockEntity.addItem(null, container, stack, null);
    }

    // ---- carried items ----

    public static List<ItemStack> getHaul(Villager villager) {
        List<ItemStack> stored = villager.getAttached(HAUL);
        List<ItemStack> copy = new ArrayList<>();
        if (stored != null) {
            for (ItemStack stack : stored) {
                if (!stack.isEmpty()) copy.add(stack.copy());
            }
        }
        return copy;
    }

    public static int haulCount(Villager villager) {
        int total = 0;
        for (ItemStack stack : getHaul(villager)) {
            total += stack.getCount();
        }
        return total;
    }

    public static void addToHaul(Villager villager, ItemStack incoming) {
        List<ItemStack> haul = getHaul(villager);
        ItemStack remaining = incoming.copy();
        for (ItemStack existing : haul) {
            if (remaining.isEmpty()) break;
            if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int room = existing.getMaxStackSize() - existing.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
        if (!remaining.isEmpty()) {
            haul.add(remaining);
        }
        setHaul(villager, haul);
    }

    public static void setHaul(Villager villager, List<ItemStack> haul) {
        if (haul.isEmpty()) {
            villager.removeAttached(HAUL);
        } else {
            villager.setAttached(HAUL, haul);
        }
    }

    /** Drops everything the villager is carrying on the ground. */
    public static void dropHaul(Villager villager) {
        for (ItemStack stack : getHaul(villager)) {
            villager.spawnAtLocation(stack);
        }
        villager.removeAttached(HAUL);
    }
}
