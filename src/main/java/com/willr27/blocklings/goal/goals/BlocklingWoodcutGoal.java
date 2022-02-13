package com.willr27.blocklings.goal.goals;

import com.willr27.blocklings.block.BlockUtil;
import com.willr27.blocklings.entity.EntityUtil;
import com.willr27.blocklings.entity.entities.blockling.BlocklingEntity;
import com.willr27.blocklings.entity.entities.blockling.BlocklingHand;
import com.willr27.blocklings.goal.IHasTargetGoal;
import com.willr27.blocklings.goal.goals.target.BlocklingWoodcutTargetGoal;
import com.willr27.blocklings.item.DropUtil;
import com.willr27.blocklings.item.ToolType;
import com.willr27.blocklings.item.ToolUtil;
import com.willr27.blocklings.skill.skills.GeneralSkills;
import com.willr27.blocklings.skill.skills.WoodcuttingSkills;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Chops the targeted tree.
 */
public class BlocklingWoodcutGoal extends BlocklingGatherGoal<BlocklingWoodcutTargetGoal> implements IHasTargetGoal<BlocklingWoodcutTargetGoal>
{
    /**
     * The log whitelist.
     */
    @Nonnull
    public final GoalWhitelist logWhitelist;

    /**
     * The associated target goal.
     */
    @Nonnull
    private final BlocklingWoodcutTargetGoal targetGoal;

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
    public BlocklingWoodcutGoal(@Nonnull UUID id, @Nonnull BlocklingEntity blockling, @Nonnull BlocklingTasks tasks)
    {
        super(id, blockling, tasks);

        targetGoal = new BlocklingWoodcutTargetGoal(this);

        logWhitelist = new GoalWhitelist("fbfbfd44-c1b0-4420-824a-270b34c866f7", "logs", Whitelist.Type.BLOCK, this);
        logWhitelist.setIsUnlocked(blockling.getSkills().getSkill(WoodcuttingSkills.WHITELIST).isBought(), false);
        BlockUtil.LOGS.forEach(log -> logWhitelist.put(log.getRegistryName(), true));
        whitelists.add(logWhitelist);

        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    @Nonnull
    public BlocklingWoodcutTargetGoal getTargetGoal()
    {
        return targetGoal;
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
    public void tick()
    {
        super.tick();
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
    protected void recalcPath(boolean force)
    {
        if (force)
        {
            Pair<BlockPos, Path> result = targetGoal.findPathToTree();

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

        // Try to improve our path each recalc by testing different logs in the tree
        for (BlockPos logBlockPos : targetGoal.getTree().logs)
        {
            if (pathTargetPositionsTested.contains(logBlockPos))
            {
                continue;
            }

            pathTargetPositionsTested.add(logBlockPos);

            if (BlockUtil.areAllAdjacentBlocksSolid(world, logBlockPos))
            {
                continue;
            }

            Path path = EntityUtil.createPathTo(blockling, logBlockPos, getRangeSq());

            if (path != null)
            {
                if (path.getDistToTarget() < this.path.getDistToTarget())
                {
                    setPathTargetPos(logBlockPos, path);
                }
            }

            return;
        }

        pathTargetPositionsTested.clear();
    }

    @Override
    protected boolean isValidPathTargetPos(@Nonnull BlockPos blockPos)
    {
        return targetGoal.getTree().logs.contains(blockPos);
    }

    @Override
    public void setPathTargetPos(@Nullable BlockPos blockPos, @Nullable Path pathToPos)
    {
        super.setPathTargetPos(blockPos, pathToPos);

        if (hasPathTargetPos())
        {
            targetGoal.changeTreeRootTo(getPathTargetPos());
        }
    }

    @Override
    public float getRangeSq()
    {
        return blockling.getStats().woodcuttingRangeSq.getValue();
    }
}
