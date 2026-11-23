package com.example.villagerworkers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Sneak + right-click a villager while holding a pickaxe, axe, hoe or fishing rod to hand it over.
 * Sneak + right-click with an empty hand takes the tool back. Sneaking is required so normal
 * trading is never disturbed.
 */
public final class VillagerToolInteraction {
    private VillagerToolInteraction() {}

    private static final int MAX_LINK_DISTANCE = 32;
    /** The chest each player has selected with the stick (server thread only). */
    private static final Map<UUID, GlobalPos> SELECTED_CHEST = new HashMap<>();

    public static void register() {
        UseEntityCallback.EVENT.register(VillagerToolInteraction::onUse);
        UseBlockCallback.EVENT.register(VillagerToolInteraction::onUseBlock);
    }

    private static void say(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    private static boolean isStorageBlock(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock || block instanceof BarrelBlock;
    }

    /** Sneak + right-click a chest or barrel with a stick: select it for linking. */
    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
                                                BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isShiftKeyDown()
                || !player.getItemInHand(hand).is(Items.STICK)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        if (!isStorageBlock(level, pos)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        GlobalPos selection = GlobalPos.of(level.dimension(), pos.immutable());
        if (selection.equals(SELECTED_CHEST.get(player.getUUID()))) {
            SELECTED_CHEST.remove(player.getUUID());
            say(player, "Chest deselected.");
        } else {
            SELECTED_CHEST.put(player.getUUID(), selection);
            say(player, "Chest selected. Sneak + right-click villagers with the stick to link them.");
        }
        return InteractionResult.SUCCESS;
    }

    /** Stick on a villager: link it to the selected chest, or unlink it if nothing is selected. */
    private static InteractionResult linkVillager(Player player, Level level, Villager villager) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        GlobalPos selection = SELECTED_CHEST.get(player.getUUID());
        if (selection == null) {
            if (VillagerStorage.getChest(villager) != null) {
                VillagerStorage.clearChest(villager);
                VillagerStorage.dropHaul(villager);
                say(player, "Chest link removed.");
            } else {
                say(player, "Select a chest first: sneak + right-click a chest or barrel with a stick.");
            }
            return InteractionResult.SUCCESS;
        }
        if (selection.dimension() != level.dimension()) {
            say(player, "That chest is in another dimension.");
        } else if (VillagerStorage.containerAt((ServerLevel) level, selection.pos()) == null) {
            SELECTED_CHEST.remove(player.getUUID());
            say(player, "That chest no longer exists.");
        } else if (villager.blockPosition().distSqr(selection.pos()) > MAX_LINK_DISTANCE * MAX_LINK_DISTANCE) {
            say(player, "Chest is too far from this villager (max " + MAX_LINK_DISTANCE + " blocks).");
        } else {
            VillagerStorage.setChest(villager, selection.pos());
            level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                    SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
            say(player, "Villager linked to chest.");
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult onUse(Player player, Level level, InteractionHand hand,
                                           Entity entity, EntityHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof Villager villager) || villager.isBaby()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.STICK)) {
            return linkVillager(player, level, villager);
        }
        ItemStack current = villager.getMainHandItem();
        ToolRole newRole = ToolRole.of(held);

        if (newRole == null) {
            // Empty hand: take the villager's tool back (if it has one).
            if (!held.isEmpty() || ToolRole.of(current) == null) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            ItemStack tool = current.copy();
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(tool);
            player.displayClientMessage(Component.literal("Villager gave the tool back."), true);
            return InteractionResult.SUCCESS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack given = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (!current.isEmpty()) {
            player.getInventory().placeItemBackInInventory(current.copy());
        }
        villager.setItemSlot(EquipmentSlot.MAINHAND, given);
        // Above 1.0 means "always drop", even if the villager is not killed by a player.
        villager.setDropChance(EquipmentSlot.MAINHAND, 2.0F);

        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        player.displayClientMessage(Component.literal("Villager is now a " + newRole.displayName + "."), true);
        return InteractionResult.SUCCESS;
    }
}
