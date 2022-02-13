package com.willr27.blocklings.goal.goals;

import com.willr27.blocklings.block.BlockUtil;
import com.willr27.blocklings.entity.entities.blockling.BlocklingEntity;
import com.willr27.blocklings.entity.entities.blockling.BlocklingHand;
import com.willr27.blocklings.item.ToolType;
import com.willr27.blocklings.skill.skills.GeneralSkills;
import com.willr27.blocklings.skill.skills.WoodcuttingSkills;
import com.willr27.blocklings.task.BlocklingTasks;
import com.willr27.blocklings.goal.goals.target.BlocklingWoodcutTargetGoalOLD;
import com.willr27.blocklings.goal.IHasTargetGoalOLD;
import com.willr27.blocklings.item.DropUtil;
import com.willr27.blocklings.item.ToolUtil;
import com.willr27.blocklings.whitelist.GoalWhitelist;
import com.willr27.blocklings.whitelist.Whitelist;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chops the targeted log/tree.
 */
public class BlocklingWoodcutGoalOLD extends BlocklingGatherGoalOLD<BlocklingWoodcutTargetGoalOLD> implements IHasTargetGoalOLD<BlocklingWoodcutTargetGoalOLD>
{
    /**
     * The log whitelist.
     */
    public final GoalWhitelist logWhitelist;

    /**
     * The associated target goal.
     */
    private final BlocklingWoodcutTargetGoalOLD targetGoal;

    /**
     * @param id the id associated with the owning task of this goal.
     * @param blockling the blockling the goal is assigned to.
     * @param tasks the associated tasks.
     */
    public BlocklingWoodcutGoalOLD(UUID id, BlocklingEntity blockling, BlocklingTasks tasks)
    {
        super(id, blockling, tasks);

        targetGoal = new BlocklingWoodcutTargetGoalOLD(this);

        logWhitelist = new GoalWhitelist("fbfbfd44-c1b0-4420-824a-270b34c866f7", "logs", Whitelist.Type.BLOCK, this);
        logWhitelist.setIsUnlocked(blockling.getSkills().getSkill(WoodcuttingSkills.WHITELIST).isBought(), false);
        BlockUtil.LOGS.forEach(log -> logWhitelist.put(log.getRegistryName(), true));
        whitelists.add(logWhitelist);

        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    @Nonnull
    public BlocklingWoodcutTargetGoalOLD getTargetGoal()
    {
        return targetGoal;
    }

    @Override
    public boolean canUse()
    {
        if (!super.canUse())
        {
            return false;
        }

        if (blockling.getSkills().getSkill(GeneralSkills.AUTOSWITCH).isBought())
        {
            blockling.getEquipment().trySwitchToBestTool(BlocklingHand.BOTH, ToolType.AXE);
        }

        if (!canHarvestTargetPos())
        {
            return false;
        }

        calculatePathToTree();

        if (isStuck())
        {
            getTargetGoal().markBad();

            return false;
        }

        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        if (!super.canContinueToUse())
        {
            return false;
        }

        if (isStuck())
        {
            getTargetGoal().markBad();

            return false;
        }

        return true;
    }

    @Override
    protected void tickGather()
    {
        super.tickGather();

        if (blockling.getSkills().getSkill(GeneralSkills.AUTOSWITCH).isBought())
        {
            blockling.getEquipment().trySwitchToBestTool(BlocklingHand.BOTH, ToolType.AXE);
        }

        ItemStack mainStack = blockling.getMainHandItem();
        ItemStack offStack = blockling.getOffhandItem();

        BlockPos targetBlockPos = targetGoal.getTargetPos();
        BlockState targetBlockState = world.getBlockState(targetBlockPos);
        Block targetBlock = targetBlockState.getBlock();

        boolean mainCanHarvest = ToolUtil.canToolHarvestBlock(mainStack, targetBlockState);
        boolean offCanHarvest = ToolUtil.canToolHarvestBlock(offStack, targetBlockState);

        if (mainCanHarvest || offCanHarvest)
        {
            blockling.getActions().gather.tryStart();

            if (blockling.getActions().gather.isRunning())
            {
                float blocklingDestroySpeed = blockling.getStats().woodcuttingSpeed.getValue();
                float mainDestroySpeed = mainCanHarvest ? ToolUtil.getToolWoodcuttingSpeedWithEnchantments(mainStack) : 0.0f;
                float offDestroySpeed = offCanHarvest ? ToolUtil.getToolWoodcuttingSpeedWithEnchantments(offStack) : 0.0f;

                float destroySpeed = blocklingDestroySpeed + mainDestroySpeed + offDestroySpeed;
                float blockStrength = targetBlockState.getDestroySpeed(world, targetGoal.getTargetPos()) + 1.5f;

                blockling.getStats().hand.setValue(BlocklingHand.fromBooleans(mainCanHarvest, offCanHarvest));

                float progress = destroySpeed / blockStrength / 100.0f;
                blockling.getActions().gather.tick(progress);

                if (blockling.getActions().gather.isFinished())
                {
                    blockling.getActions().gather.stop();
                    blockling.getStats().woodcuttingXp.incrementValue((int) blockStrength);

                    for (ItemStack stack : DropUtil.getDrops(DropUtil.Context.WOODCUTTING, blockling, targetBlockPos, mainCanHarvest ? mainStack : ItemStack.EMPTY, offCanHarvest ? offStack : ItemStack.EMPTY))
                    {
                        stack = blockling.getEquipment().addItem(stack);
                        blockling.dropItemStack(stack);
                    }

                    if (mainStack.hurt(mainCanHarvest ? blockling.getSkills().getSkill(WoodcuttingSkills.HASTY).isBought() ? 2 : 1 : 0, blockling.getRandom(), null))
                    {
                        mainStack.shrink(1);
                    }

                    if (offStack.hurt(offCanHarvest ? blockling.getSkills().getSkill(WoodcuttingSkills.HASTY).isBought() ? 2 : 1 : 0, blockling.getRandom(), null))
                    {
                        offStack.shrink(1);
                    }

                    blockling.incLogsChoppedRecently();

                    world.destroyBlock(targetBlockPos, false);
                    world.destroyBlockProgress(blockling.getId(), targetBlockPos, -1);

                    if (blockling.getSkills().getSkill(WoodcuttingSkills.LUMBER_AXE).isBought())
                    {
                        for (BlockPos surroundingPos : BlockUtil.getSurroundingBlockPositions(targetBlockPos))
                        {
                            Block surroundingBlock = world.getBlockState(surroundingPos).getBlock();

                            if (targetGoal.isValidTarget(surroundingPos))
                            {
                                for (ItemStack stack : DropUtil.getDrops(DropUtil.Context.WOODCUTTING, blockling, surroundingPos, mainCanHarvest ? mainStack : ItemStack.EMPTY, offCanHarvest ? offStack : ItemStack.EMPTY))
                                {
                                    stack = blockling.getEquipment().addItem(stack);
                                    blockling.dropItemStack(stack);
                                }

                                world.destroyBlock(surroundingPos, false);

                                if (blockling.getSkills().getSkill(WoodcuttingSkills.REPLANTER).isBought())
                                {
                                    if (BlockUtil.DIRTS.contains(world.getBlockState(surroundingPos.below()).getBlock()))
                                    {
                                        Block saplingBlock = BlockUtil.getSaplingFromLog(surroundingBlock);

                                        if (saplingBlock != null)
                                        {
                                            ItemStack itemStack = new ItemStack(saplingBlock);

                                            if (blockling.getEquipment().has(itemStack))
                                            {
                                                blockling.getEquipment().take(itemStack);

                                                world.setBlock(surroundingPos, saplingBlock.defaultBlockState(), 3);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else
                    {
                        if (blockling.getSkills().getSkill(WoodcuttingSkills.REPLANTER).isBought())
                        {
                            if (BlockUtil.DIRTS.contains(world.getBlockState(targetBlockPos.below()).getBlock()))
                            {
                                Block saplingBlock = BlockUtil.getSaplingFromLog(targetBlock);

                                if (saplingBlock != null)
                                {
                                    ItemStack itemStack = new ItemStack(saplingBlock);

                                    if (blockling.getEquipment().has(itemStack))
                                    {
                                        blockling.getEquipment().take(itemStack);

                                        world.setBlock(targetBlockPos, saplingBlock.defaultBlockState(), 3);
                                    }
                                }
                            }
                        }
                    }

                    if (blockling.getSkills().getSkill(WoodcuttingSkills.LEAF_BLOWER).isBought())
                    {
                        for (BlockPos surroundingPos : BlockUtil.getSurroundingBlockPositions(targetBlockPos))
                        {
                            if (BlockUtil.isLeaf(world.getBlockState(surroundingPos).getBlock()))
                            {
                                if (blockling.getSkills().getSkill(WoodcuttingSkills.TREE_SURGEON).isBought())
                                {
                                    for (ItemStack stack : DropUtil.getDrops(DropUtil.Context.WOODCUTTING, blockling, surroundingPos, mainCanHarvest ? mainStack : ItemStack.EMPTY, offCanHarvest ? offStack : ItemStack.EMPTY))
                                    {
                                        stack = blockling.getEquipment().addItem(stack);
                                        blockling.dropItemStack(stack);
                                    }
                                }

                                world.destroyBlock(surroundingPos, false);
                            }
                        }
                    }

                    recalc();
                }
                else
                {
                    world.destroyBlockProgress(blockling.getId(), targetBlockPos, BlockUtil.calcBlockBreakProgress(blockling.getActions().gather.count()));
                }
            }
        }
        else
        {
            world.destroyBlockProgress(blockling.getId(), targetBlockPos, -1);
            blockling.getActions().gather.stop();
        }
    }

    @Override
    protected void recalc()
    {
        if (isStuck())
        {
            targetGoal.markBad();
        }

        if (!getTargetGoal().isTargetValid())
        {
            getTargetGoal().recalcTarget();
        }

        if (targetGoal.hasTarget())
        {
            calculatePathToTree();
        }
    }

    /**
     * Finds a path towards the tree.
     */
    private void calculatePathToTree()
    {
        List<BlockPos> sortedLogBlockPositions = targetGoal.getTree().logs.stream().sorted(Comparator.comparingInt(Vector3i::getY)).collect(Collectors.toList());

        BlockPos closestPos = null;
        Path closestPath = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (BlockPos testPathTargetPos : sortedLogBlockPositions)
        {
            Path path = createPath(testPathTargetPos);

            if (path != null)
            {
                if (isInRange(testPathTargetPos))
                {
                    setPathTargetPos(testPathTargetPos, path);

                    return;
                }

                double distanceSq = testPathTargetPos.distSqr(path.getTarget());

                if (distanceSq < closestDistanceSq)
                {
                    closestPos = testPathTargetPos;
                    closestPath = path;
                    closestDistanceSq = distanceSq;
                }
            }
        }

        if (closestPath != null)
        {
            setPathTargetPos(closestPos, closestPath);
        }
    }

    @Override
    float getRangeSq()
    {
        return blockling.getStats().woodcuttingRangeSq.getValue();
    }
}
