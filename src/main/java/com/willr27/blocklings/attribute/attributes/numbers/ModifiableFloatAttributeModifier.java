package com.willr27.blocklings.attribute.attributes.numbers;

import com.willr27.blocklings.attribute.IModifiable;
import com.willr27.blocklings.attribute.IModifier;
import com.willr27.blocklings.attribute.Operation;
import com.willr27.blocklings.entity.entities.blockling.BlocklingEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A modifiable float attribute modifier.
 * Used to create trees of modifiers with a single root attribute.
 */
public class ModifiableFloatAttributeModifier extends ModifiableFloatAttribute implements IModifier<Float>
{
    /**
     * The attributes the modifier is associated with.
     */
    @Nonnull
    public final List<IModifiable<Float>> attributes = new ArrayList<>();

    /**
     * The operation to be performed on the associated attribute and the modifier.
     */
    @Nonnull
    public final Operation operation;

    /**
     * @param id the id of the attribute.
     * @param key the key used to identify the attribute (for things like translation text components).
     * @param blockling the blockling.
     * @param initialValue the initial value of the attribute.
     * @param operation the operation to be performed on the associated attribute and the modifier.
     * @param displayStringValueFunction the function used to provide the string representation of the value.
     * @param displayStringNameSupplier the supplier used to provide the string representation of display name.
     * @param isEnabled whether the attribute is currently enabled.
     * @param modifiers the initial list of modifiers associated with the attribute.
     */
    public ModifiableFloatAttributeModifier(@Nonnull String id, @Nonnull String key, @Nonnull BlocklingEntity blockling, float initialValue, @Nonnull Operation operation, @Nullable Function<Float, String> displayStringValueFunction, @Nullable Supplier<String> displayStringNameSupplier, boolean isEnabled, @Nonnull IModifier<Float>... modifiers)
    {
        super(id, key, blockling, initialValue, displayStringValueFunction, displayStringNameSupplier, isEnabled, modifiers);
        this.operation = operation;
    }

    @Override
    public void calculate()
    {
        super.calculate();

        attributes.forEach(IModifiable::calculate);
    }

    @Override
    public void setBaseValue(Float baseValue, boolean sync)
    {
        super.setBaseValue(value, sync);

        attributes.forEach(IModifiable::calculate);
    }

    @Override
    @Nonnull
    public List<IModifiable<Float>> getAttributes()
    {
        return attributes;
    }

    @Override
    @Nonnull
    public Operation getOperation()
    {
        return operation;
    }

    @Override
    public void setIsEnabled(boolean isEnabled, boolean sync)
    {
        super.setIsEnabled(isEnabled, sync);

        attributes.forEach(IModifiable::calculate);
    }
}
