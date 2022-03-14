package com.willr27.blocklings.goal.goals;

import com.willr27.blocklings.block.BlockUtil;
import com.willr27.blocklings.entity.EntityUtil;
import com.willr27.blocklings.entity.entities.blockling.BlocklingEntity;
import com.willr27.blocklings.entity.entities.blockling.BlocklingHand;
import com.willr27.blocklings.item.DropUtil;
import com.willr27.blocklings.item.ToolType;
import com.willr27.blocklings.item.ToolUtil;
import com.willr27.blocklings.skill.skills.MiningSkills;
import com.willr27.blocklings.task.BlocklingTasks;
import com.willr27.blocklings.whitelist.GoalWhitelist;
import com.willr27.blocklings.whitelist.Whitelist;
import javafx.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Mines the targeted ore/vein.
 */
public class BlocklingMineGoal extends BlocklingGatherGoal
{
    /**
     * The x and z search radius.
     */
    private static final int SEARCH_RADIUS_X = 8;

    /**
     * The y search radius.
     */
    private static final int SEARCH_RADIUS_Y = 8;

    /**
     * The list of block positions in the current vein.
     */
    @Nonnull
    public final List<BlockPos> veinBlockPositions = new ArrayList<>();

    /**
     * The ore whitelist.
     */
    @Nonnull
    public final GoalWhitelist oreWhitelist;

    /**
     * The set of positions we have attempted to use as path targets so far.
     */
    @Nonnull
    private final Set<BlockPos> pathTargetPositionsTested = new HashSet<>();

    /**
     * @param id the id associated with the owning task of this goal.
     * @param blockling the blockling the goal is assigned to.
     * @param tasks the associated tasks.
     */
    public BlocklingMineGoal(@Nonnull UUID id, @Nonnull BlocklingEntity blockling, @Nonnull BlocklingTasks tasks)
    {
        super(id, blockling, tasks);

        oreWhitelist = new GoalWhitelist("24d7135e-607b-413b-a2a7-00d19119b9de", "ores", Whitelist.Type.BLOCK, this);
        oreWhitelist.setIsUnlocked(blockling.getSkills().getSkill(MiningSkills.WHITELIST).isBought(), false);
        BlockUtil.ORES.forEach(ore -> oreWhitelist.put(ore.getRegistryName(), true));
        whitelists.add(oreWhitelist);

        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse()
    {
        return super.canContinueToUse();
    }

    @Override
    public void stop()
    {
        super.stop();

        veinBlockPositions.clear();
    }

    @Override
    protected void tickGather()
    {
        super.tickGather();

        ItemStack mainStack = blockling.getMainHandItem();
        ItemStack offStack = blockling.getOffhandItem();

        BlockPos targetPos = getTarget();
        BlockState targetBlockState = world.getBlockState(targetPos);

        boolean mainCanHarvest = ToolUtil.canToolHarvestBlock(mainStack, targetBlockState);
        boolean offCanHarvest = ToolUtil.canToolHarvestBlock(offStack, targetBlockState);

        if (mainCanHarvest || offCanHarvest)
        {
            blockling.getActions().gather.tryStart();

            if (blockling.getActions().gather.isRunning())
            {
                float blocklingDestroySpeed = blockling.getStats().miningSpeed.getValue();
                float mainDestroySpeed = mainCanHarvest ? ToolUtil.getToolMiningSpeedWithEnchantments(mainStack) : 0.0f;
                float offDestroySpeed = offCanHarvest ? ToolUtil.getToolMiningSpeedWithEnchantments(offStack) : 0.0f;

                float destroySpeed = blocklingDestroySpeed + mainDestroySpeed + offDestroySpeed;
                float blockStrength = targetBlockState.getDestroySpeed(world, targetPos);

                blockling.getStats().hand.setValue(BlocklingHand.fromBooleans(mainCanHarvest, offCanHarvest));

                float progress = destroySpeed / blockStrength / 100.0f;
                blockling.getActions().gather.tick(progress);

                if (blockling.getActions().gather.isFinished())
                {
                    blockling.getActions().gather.stop();
                    blockling.getStats().miningXp.incrementValue((int) (blockStrength * 2.0f));

                    for (ItemStack stack : DropUtil.getDrops(DropUtil.Context.MINING, blockling, targetPos, mainCanHarvest ? mainStack : ItemStack.EMPTY, offCanHarvest ? offStack : ItemStack.EMPTY))
                    {
                        stack = blockling.getEquipment().addItem(stack);
                        blockling.dropItemStack(stack);
                    }

                    if (mainStack.hurt(mainCanHarvest ? blockling.getSkills().getSkill(MiningSkills.HASTY).isBought() ? 2 : 1 : 0, blockling.getRandom(), null))
                    {
                        mainStack.shrink(1);
                    }

                    if (offStack.hurt(offCanHarvest ? blockling.getSkills().getSkill(MiningSkills.HASTY).isBought() ? 2 : 1 : 0, blockling.getRandom(), null))
                    {
                        offStack.shrink(1);
                    }

                    blockling.incOresMinedRecently();

                    world.destroyBlock(targetPos, false);
                    world.destroyBlockProgress(blockling.getId(), targetPos, -1);

                    if (blockling.getSkills().getSkill(MiningSkills.HAMMER).isBought())
                    {
                        for (BlockPos surroundingPos : BlockUtil.getSurroundingBlockPositions(targetPos))
                        {
                            if (isValidTarget(surroundingPos))
                            {
                                for (ItemStack stack : DropUtil.getDrops(DropUtil.Context.MINING, blockling, surroundingPos, mainCanHarvest ? mainStack : ItemStack.EMPTY, offCanHarvest ? offStack : ItemStack.EMPTY))
                                {
                                    stack = blockling.getEquipment().addItem(stack);
                                    blockling.dropItemStack(stack);
                                }

                                world.destroyBlock(surroundingPos, false);
                            }
                        }
                    }
                }
                else
                {
                    world.destroyBlockProgress(blockling.getId(), targetPos, BlockUtil.calcBlockBreakProgress(blockling.getActions().gather.count()));
                }
            }
        }
        else
        {
            world.destroyBlockProgress(blockling.getId(), targetPos, -1);
            blockling.getActions().gather.stop();
        }
    }

    @Override
    public void checkForAndRemoveInvalidTargets()
    {
        for (BlockPos blockPos : new ArrayList<>(veinBlockPositions))
        {
            if (!isValidTarget(blockPos))
            {
                markBad(blockPos);
            }
        }
    }

    @Override
    public boolean tryRecalcTarget()
    {
        if (isTargetValid())
        {
            return canHarvestTargetPos();
        }
        else
        {
            markTargetBad();
        }

        if (veinBlockPositions.isEmpty())
        {
            if (!tryFindVein())
            {
                return false;
            }

            Pair<BlockPos, Path> pathToVein = findPathToVein();

            if (pathToVein == null)
            {
                return false;
            }

            setPathTargetPos(pathToVein.getKey(), pathToVein.getValue(), false);
        }

        setTarget((BlockPos) veinBlockPositions.toArray()[veinBlockPositions.size() - 1]);

        return canHarvestTargetPos();
    }

    @Override
    public void markEntireTargetBad()
    {
        while (!veinBlockPositions.isEmpty())
        {
            markBad(veinBlockPositions.get(0));
        }
    }

    @Override
    public void markBad(@Nonnull BlockPos blockPos)
    {
        super.markBad(blockPos);

        veinBlockPositions.remove(blockPos);
    }

    @Override
    protected boolean isValidTargetBlock(@Nonnull Block block)
    {
        return oreWhitelist.isEntryWhitelisted(block);
    }

    @Nonnull
    @Override
    protected ToolType getToolType()
    {
        return ToolType.PICKAXE;
    }

    /**
     * Tries to find the nearest vein.
     *
     * @return true if a vein was found.
     */
    private boolean tryFindVein()
    {
        BlockPos blocklingBlockPos = blockling.blockPosition();

        List<BlockPos> veinBlockPositions = new ArrayList<>();
        List<BlockPos> testedBlockPositions = new ArrayList<>();

        double closestVeinDistSq = Float.MAX_VALUE;

        for (int i = -SEARCH_RADIUS_X; i <= SEARCH_RADIUS_X; i++)
        {
            for (int j = -SEARCH_RADIUS_Y; j <= SEARCH_RADIUS_Y; j++)
            {
                for (int k = -SEARCH_RADIUS_X; k <= SEARCH_RADIUS_X; k++)
                {
                    BlockPos testBlockPos = blocklingBlockPos.offset(i, j, k);

                    if (testedBlockPositions.contains(testBlockPos))
                    {
                        continue;
                    }

                    if (isValidTarget(testBlockPos))
                    {
                        List<BlockPos> veinBlockPositionsToTest = findVeinFrom(testBlockPos);

                        boolean canSeeVein = false;

                        for (BlockPos veinBlockPos : veinBlockPositionsToTest)
                        {
                            if (!testedBlockPositions.contains(veinBlockPos))
                            {
                                testedBlockPositions.add(veinBlockPos);
                            }

                            if (!canSeeVein && EntityUtil.canSee(blockling, veinBlockPos))
                            {
                                canSeeVein = true;
                            }
                        }

                        if (!canSeeVein)
                        {
                            continue;
                        }

                        for (BlockPos veinBlockPos : veinBlockPositionsToTest)
                        {
                            float distanceSq = (float) blockling.distanceToSqr(veinBlockPos.getX() + 0.5f, veinBlockPos.getY() + 0.5f, veinBlockPos.getZ() + 0.5f);

                            if (distanceSq < closestVeinDistSq)
                            {
                                closestVeinDistSq = distanceSq;
                                veinBlockPositions = veinBlockPositionsToTest;

                                break;
                            }
                        }
                    }
                }
            }
        }

        if (!veinBlockPositions.isEmpty())
        {
            this.veinBlockPositions.clear();
            this.veinBlockPositions.addAll(veinBlockPositions);

            return true;
        }

        return false;
    }

    /**
     * Returns a vein from the given starting block pos.
     *
     * @param startingBlockPos the starting block pos.
     * @return the list of block positions in the vein.
     */
    @Nonnull
    private List<BlockPos> findVeinFrom(@Nonnull BlockPos startingBlockPos)
    {
        List<BlockPos> veinBlockPositionsToTest = new ArrayList<>();
        List<BlockPos> veinBlockPositions = new ArrayList<>();

        veinBlockPositionsToTest.add(startingBlockPos);
        veinBlockPositions.add(startingBlockPos);

        while (!veinBlockPositionsToTest.isEmpty())
        {
            BlockPos testBlockPos = veinBlockPositionsToTest.stream().findFirst().get();

            BlockPos[] surroundingBlockPositions = new BlockPos[]
                    {
                            testBlockPos.offset(-1, 0, 0),
                            testBlockPos.offset(1, 0, 0),
                            testBlockPos.offset(0, -1, 0),
                            testBlockPos.offset(0, 1, 0),
                            testBlockPos.offset(0, 0, -1),
                            testBlockPos.offset(0, 0, 1),
                    };

            for (BlockPos surroundingPos : surroundingBlockPositions)
            {
                if (isValidTarget(surroundingPos))
                {
                    if (!veinBlockPositions.contains(surroundingPos))
                    {
                        veinBlockPositions.add(surroundingPos);
                        veinBlockPositionsToTest.add(surroundingPos);
                    }
                }
            }

            veinBlockPositionsToTest.remove(testBlockPos);
        }

        return veinBlockPositions;
    }

    /**
     * Finds the first valid path to the vein, not necessarily the most optimal.
     *
     * @return the path target position and the path to the vein, or null if no path could be found.
     */
    @Nullable
    public Pair<BlockPos, Path> findPathToVein()
    {
        for (BlockPos veinBlockPos : veinBlockPositions)
        {
            if (BlockUtil.areAllAdjacentBlocksSolid(world, veinBlockPos))
            {
                continue;
            }

            if (isBadPathTargetPos(veinBlockPos))
            {
                continue;
            }

            Path path = EntityUtil.createPathTo(blockling, veinBlockPos, getRangeSq());

            if (path != null)
            {
                return new Pair<>(veinBlockPos, path);
            }
        }

        return null;
    }

    /**
     * Sets the root vein position to the given block pos.
     * Will then recalculate the vein.
     *
     * @param blockPos the block pos to use as the vein root.
     */
    public void changeVeinRootTo(@Nonnull BlockPos blockPos)
    {
        veinBlockPositions.clear();
        veinBlockPositions.addAll(findVeinFrom(blockPos));
    }

    @Override
    protected void recalcPath(boolean force)
    {
        if (force)
        {
            Pair<BlockPos, Path> result = findPathToVein();

            if (result != null)
            {
                setPathTargetPos(result.getKey(), result.getValue());
            }
            else
            {
                setPathTargetPos(null, null);
            }

            return;
        }

        // Try to improve our path each recalc by testing different blocks in the vein
        for (BlockPos veinBlockPos : veinBlockPositions)
        {
            if (pathTargetPositionsTested.contains(veinBlockPos))
            {
                continue;
            }

            pathTargetPositionsTested.add(veinBlockPos);

            if (BlockUtil.areAllAdjacentBlocksSolid(world, veinBlockPos))
            {
                continue;
            }

            Path path = EntityUtil.createPathTo(blockling, veinBlockPos, getRangeSq());

            if (path != null)
            {
                if (path.getDistToTarget() < this.path.getDistToTarget())
                {
                    setPathTargetPos(veinBlockPos, path);
                }
            }

            return;
        }

        pathTargetPositionsTested.clear();
    }

    @Override
    protected boolean isValidPathTargetPos(@Nonnull BlockPos blockPos)
    {
        return veinBlockPositions.contains(blockPos);
    }

    @Override
    public void setPathTargetPos(@Nullable BlockPos blockPos, @Nullable Path pathToPos)
    {
        super.setPathTargetPos(blockPos, pathToPos);

        if (hasPathTargetPos())
        {
            changeVeinRootTo(getPathTargetPos());
        }
    }

    @Override
    public float getRangeSq()
    {
        return blockling.getStats().miningRangeSq.getValue();
    }
}
