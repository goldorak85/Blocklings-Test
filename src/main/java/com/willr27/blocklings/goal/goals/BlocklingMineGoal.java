package com.willr27.blocklings.goal.goals;

import com.willr27.blocklings.block.BlockUtil;
import com.willr27.blocklings.entity.EntityUtil;
import com.willr27.blocklings.entity.entities.blockling.BlocklingEntity;
import com.willr27.blocklings.entity.entities.blockling.BlocklingHand;
import com.willr27.blocklings.goal.IHasTargetGoal;
import com.willr27.blocklings.goal.goals.target.BlocklingMineTargetGoal;
import com.willr27.blocklings.item.DropUtil;
import com.willr27.blocklings.item.ToolType;
import com.willr27.blocklings.item.ToolUtil;
import com.willr27.blocklings.skill.skills.GeneralSkills;
import com.willr27.blocklings.skill.skills.MiningSkills;
import com.willr27.blocklings.task.BlocklingTasks;
import com.willr27.blocklings.whitelist.GoalWhitelist;
import com.willr27.blocklings.whitelist.Whitelist;
import javafx.util.Pair;
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
public class BlocklingMineGoal extends BlocklingGatherGoal<BlocklingMineTargetGoal> implements IHasTargetGoal<BlocklingMineTargetGoal>
{
    /**
     * The ore whitelist.
     */
    @Nonnull
    public final GoalWhitelist oreWhitelist;

    /**
     * The associated target goal.
     */
    @Nonnull
    private final BlocklingMineTargetGoal targetGoal;

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

        targetGoal = new BlocklingMineTargetGoal(this);

        oreWhitelist = new GoalWhitelist("24d7135e-607b-413b-a2a7-00d19119b9de", "ores", Whitelist.Type.BLOCK, this);
        oreWhitelist.setIsUnlocked(blockling.getSkills().getSkill(MiningSkills.WHITELIST).isBought(), false);
        BlockUtil.ORES.forEach(ore -> oreWhitelist.put(ore.getRegistryName(), true));
        whitelists.add(oreWhitelist);

        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    @Nonnull
    public BlocklingMineTargetGoal getTargetGoal()
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
            blockling.getEquipment().trySwitchToBestTool(BlocklingHand.BOTH, ToolType.PICKAXE);
        }

        ItemStack mainStack = blockling.getMainHandItem();
        ItemStack offStack = blockling.getOffhandItem();

        BlockPos targetBlockPos = targetGoal.getTargetPos();
        BlockState targetBlockState = world.getBlockState(targetBlockPos);

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
                float blockStrength = targetBlockState.getDestroySpeed(world, targetGoal.getTargetPos());

                blockling.getStats().hand.setValue(BlocklingHand.fromBooleans(mainCanHarvest, offCanHarvest));

                float progress = destroySpeed / blockStrength / 100.0f;
                blockling.getActions().gather.tick(progress);

                if (blockling.getActions().gather.isFinished())
                {
                    blockling.getActions().gather.stop();
                    blockling.getStats().miningXp.incrementValue((int) (blockStrength * 2.0f));

                    for (ItemStack stack : DropUtil.getDrops(DropUtil.Context.MINING, blockling, targetBlockPos, mainCanHarvest ? mainStack : ItemStack.EMPTY, offCanHarvest ? offStack : ItemStack.EMPTY))
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

                    world.destroyBlock(targetBlockPos, false);
                    world.destroyBlockProgress(blockling.getId(), targetBlockPos, -1);

                    if (blockling.getSkills().getSkill(MiningSkills.HAMMER).isBought())
                    {
                        for (BlockPos surroundingPos : BlockUtil.getSurroundingBlockPositions(targetBlockPos))
                        {
                            if (targetGoal.isValidTarget(surroundingPos))
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
            Pair<BlockPos, Path> result = targetGoal.findPathToVein();

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
        for (BlockPos veinBlockPos : targetGoal.veinBlockPositions)
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
        return targetGoal.veinBlockPositions.contains(blockPos);
    }

    @Override
    public void setPathTargetPos(@Nullable BlockPos blockPos, @Nullable Path pathToPos)
    {
        super.setPathTargetPos(blockPos, pathToPos);

        if (hasPathTargetPos())
        {
            targetGoal.changeVeinRootTo(getPathTargetPos());
        }
    }

    @Override
    public float getRangeSq()
    {
        return blockling.getStats().miningRangeSq.getValue();
    }
}
