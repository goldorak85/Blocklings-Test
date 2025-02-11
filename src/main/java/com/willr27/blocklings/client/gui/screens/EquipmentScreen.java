package com.willr27.blocklings.client.gui.screens;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.willr27.blocklings.client.gui.GuiTextures;
import com.willr27.blocklings.client.gui.GuiUtil;
import com.willr27.blocklings.client.gui.containers.EquipmentContainer;
import com.willr27.blocklings.client.gui.controls.TabbedControl;
import com.willr27.blocklings.entity.blockling.BlocklingEntity;
import net.minecraft.util.Hand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;

/**
 * The container screen for the blockling's main equipment inventory.
 */
@OnlyIn(Dist.CLIENT)
public class EquipmentScreen extends TabbedContainerScreen<EquipmentContainer>
{
    /**
     * @param equipmentContainer the container for the equipment.
     * @param blockling the blockling.
     */
    public EquipmentScreen(@Nonnull EquipmentContainer equipmentContainer, @Nonnull BlocklingEntity blockling)
    {
        super(equipmentContainer, blockling);
    }

    @Override
    protected void renderBg(@Nonnull MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY)
    {
        GuiUtil.bindTexture(GuiTextures.EQUIPMENT);
        blit(matrixStack, contentLeft, contentTop, 0, 0, TabbedControl.CONTENT_WIDTH, TabbedControl.CONTENT_HEIGHT);

        if (blockling.getEquipment().hasToolEquipped(Hand.MAIN_HAND))
        {
            fill(matrixStack, contentLeft + 12, contentTop + 62, contentLeft + 12 + 16, contentTop + 62 + 16, 0xff8b8b8b);
        }

        if (blockling.getEquipment().hasToolEquipped(Hand.OFF_HAND))
        {
            fill(matrixStack, contentLeft + 36, contentTop + 62, contentLeft + 36 + 16, contentTop + 62 + 16, 0xff8b8b8b);
        }

        GuiUtil.renderEntityOnScreen(matrixStack, centerX - 56, centerY - 38, 27, centerX - 56 - mouseX, centerY - 38 - mouseY, blockling);

        super.renderBg(matrixStack, partialTicks, mouseX, mouseY);
    }
}
