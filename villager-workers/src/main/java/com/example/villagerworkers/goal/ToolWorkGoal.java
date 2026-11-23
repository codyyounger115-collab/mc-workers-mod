package com.example.villagerworkers.goal;

import com.example.villagerworkers.ToolRole;
import com.example.villagerworkers.VillagerStorage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Shared behaviour for every villager job: find a target block, walk to it, work on it for a
 * while, then finish. A job only runs while the villager holds the matching tool, during the
 * day, and while it is not trading, sleeping, panicking or hiding.
 *
 * If the villager is linked to a chest, everything it gathers is carried (saved with the
 * villager) and delivered to the chest once it carries enough, or when there is no more work.
 * Without a link, items simply drop on the ground.
 */
public abstract class ToolWorkGoal extends Goal {
    private static final int SEARCH_COOLDOWN = 100;
    private static final int MAX_TICKS_PER_TASK = 20 * 60;
    /** Deliver to the chest once the villager carries this many items. */
    private static final int DELIVER_THRESHOLD = 16;
    private static final double CHEST_REACH_SQR = 3.3 * 3.3;

    protected final Villager villager;
    private final ToolRole role;
    private final double speed;

    protected BlockPos target;
    private Path pendingPath;
    private boolean delivering;
    private boolean working;
    private boolean finished;
    private int workTicks;
    private int workDuration;
    private int elapsed;
    private int repathAttempts;
    private int nextSearchTick;

    protected ToolWorkGoal(Villager villager, ToolRole role, double speed) {
        this.villager = villager;
        this.role = role;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // ---- hooks for the individual jobs -------------------------------------------------

    /** Nearest-first list of candidate target blocks (keep it short, it is path-checked). */
    protected abstract List<BlockPos> findCandidates();

    /** True while {@link #target} is still something worth working on. */
    protected abstract boolean isTargetValid();

    /** Called when the villager arrives. Returns how many ticks the work takes. */
    protected abstract int beginWork();

    /** Called every tick while working. */
    protected void onWorkTick(int ticks, int duration) {}

    /** Called when the work time has elapsed. */
    protected abstract void completeWork();

    /** Called when a work task stops for any reason. */
    protected void onStop() {}

    protected int pathAccuracy() {
        return 1;
    }

    protected double reachDistanceSqr() {
        return 2.8 * 2.8;
    }

    // ---- helpers -----------------------------------------------------------------------

    protected ServerLevel level() {
        return (ServerLevel) villager.level();
    }

    protected ItemStack tool() {
        return villager.getMainHandItem();
    }

    /** Scans a box around the villager and returns the nearest matches, nearest first. */
    protected List<BlockPos> scan(int radius, int down, int up, Predicate<BlockPos> test, int limit) {
        ServerLevel level = level();
        BlockPos origin = villager.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-radius, -down, -radius), origin.offset(radius, up, radius))) {
            if (level.hasChunkAt(p) && test.test(p)) {
                found.add(p.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /** Gathered item: carried to the linked chest, or dropped if there is no chest. */
    protected void collect(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (VillagerStorage.getChest(villager) != null) {
            VillagerStorage.addToHaul(villager, stack);
        } else {
            villager.spawnAtLocation(stack);
        }
    }

    /** Collects the block's drops (as if broken with the villager's tool) and spawns its XP. */
    protected void collectDrops(BlockState state, BlockPos pos) {
        ServerLevel level = level();
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, villager, tool())) {
            collect(drop);
        }
        state.spawnAfterBreak(level, pos, tool(), true);
    }

    /** Collects the block's loot, then removes the block. */
    protected void breakBlock(BlockPos pos) {
        ServerLevel level = level();
        collectDrops(level.getBlockState(pos), pos);
        level.destroyBlock(pos, false, villager);
    }

    /** Wears the tool down. Returns true if it broke (the villager's hand is then empty). */
    protected boolean damageTool(int amount) {
        ItemStack tool = tool();
        if (!tool.isDamageableItem()) {
            return false;
        }
        tool.setDamageValue(tool.getDamageValue() + amount);
        if (tool.getDamageValue() >= tool.getMaxDamage()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            villager.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
            return true;
        }
        return false;
    }

    private boolean canWork(boolean needTool) {
        if (needTool && ToolRole.of(tool()) != role) return false;
        if (!villager.isAlive() || villager.isBaby() || villager.isSleeping() || villager.isTrading()) return false;
        if (villager.level().getDayTime() % 24000L >= 12000L) return false; // only work in daylight
        Brain<Villager> brain = villager.getBrain();
        return !(brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE)
                || brain.isActive(Activity.RAID) || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.REST));
    }

    private boolean isInReach() {
        double limit = delivering ? CHEST_REACH_SQR : reachDistanceSqr();
        return villager.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= limit;
    }

    private void beginWorking() {
        working = true;
        workTicks = 0;
        workDuration = Math.max(1, beginWork());
    }

    /** Sets up a trip to the linked chest. Returns false if there is nothing to do or no route. */
    private boolean prepareDelivery(BlockPos chest) {
        Path path = villager.getNavigation().createPath(chest, 2);
        if (path == null || !path.canReach()) {
            return false;
        }
        target = chest;
        pendingPath = path;
        delivering = true;
        return true;
    }

    private void arrive() {
        villager.getNavigation().stop();
        if (delivering) {
            deliverHaul();
            finished = true;
        } else {
            beginWorking();
        }
    }

    private void deliverHaul() {
        ServerLevel level = level();
        Container container = VillagerStorage.containerAt(level, target);
        if (container == null) {
            // Chest was removed: forget the link and drop what we carry.
            VillagerStorage.clearChest(villager);
            VillagerStorage.dropHaul(villager);
            return;
        }
        villager.swing(InteractionHand.MAIN_HAND);
        for (ItemStack stack : VillagerStorage.getHaul(villager)) {
            ItemStack leftover = VillagerStorage.insert(container, stack);
            if (!leftover.isEmpty()) {
                villager.spawnAtLocation(leftover); // chest full
            }
        }
        VillagerStorage.setHaul(villager, List.of());
        level.playSound(null, target, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    // ---- Goal lifecycle ----------------------------------------------------------------

    @Override
    public boolean canUse() {
        if (villager.tickCount < nextSearchTick || !canWork(true)) {
            return false;
        }
        // Let the villager's own brain finish whatever it is walking to.
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return false;
        }
        nextSearchTick = villager.tickCount + SEARCH_COOLDOWN + villager.getRandom().nextInt(60);

        BlockPos chest = VillagerStorage.getChest(villager);
        int carried = chest == null ? 0 : VillagerStorage.haulCount(villager);

        // Carrying a lot: go to the chest first.
        if (carried >= DELIVER_THRESHOLD && prepareDelivery(chest)) {
            return true;
        }

        PathNavigation navigation = villager.getNavigation();
        for (BlockPos candidate : findCandidates()) {
            Path path = navigation.createPath(candidate, pathAccuracy());
            if (path != null && path.canReach()) {
                target = candidate;
                pendingPath = path;
                delivering = false;
                return true;
            }
        }

        // Nothing left to do here: hand in whatever we are carrying.
        return carried > 0 && prepareDelivery(chest);
    }

    @Override
    public boolean canContinueToUse() {
        return !finished && target != null && canWork(!delivering) && (delivering || isTargetValid());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        finished = false;
        working = false;
        workTicks = 0;
        elapsed = 0;
        repathAttempts = 0;
        if (isInReach()) {
            arrive();
        } else {
            villager.getNavigation().moveTo(pendingPath, speed);
        }
    }

    @Override
    public void tick() {
        // Our job takes priority over the brain's wandering while we are active.
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        Vec3 center = Vec3.atCenterOf(target);
        villager.getLookControl().setLookAt(center.x, center.y, center.z);

        if (++elapsed > MAX_TICKS_PER_TASK) {
            finished = true;
            return;
        }

        if (!working && !finished) {
            if (isInReach()) {
                arrive();
            } else if (villager.getNavigation().isDone()) {
                int accuracy = delivering ? 2 : pathAccuracy();
                Path path = ++repathAttempts <= 3
                        ? villager.getNavigation().createPath(target, accuracy) : null;
                if (path == null || !path.canReach()) {
                    finished = true; // can't get there, give up on this target
                } else {
                    villager.getNavigation().moveTo(path, speed);
                }
            }
            return;
        }
        if (finished) {
            return;
        }

        workTicks++;
        if (workTicks % 10 == 1) {
            villager.swing(InteractionHand.MAIN_HAND);
        }
        onWorkTick(workTicks, workDuration);
        if (workTicks >= workDuration) {
            completeWork();
            finished = true;
        }
    }

    @Override
    public void stop() {
        villager.getNavigation().stop();
        if (!delivering) {
            onStop();
        }
        target = null;
        pendingPath = null;
        working = false;
        delivering = false;
        // Short breather before looking for the next task.
        nextSearchTick = villager.tickCount + 20 + villager.getRandom().nextInt(20);
    }
}
