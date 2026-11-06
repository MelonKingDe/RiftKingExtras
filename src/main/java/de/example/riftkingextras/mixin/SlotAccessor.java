package de.example.riftkingextras.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Makes the (final) slot coordinates writable, so the inventory layout can be rebuilt. */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void riftkingextras$setX(int x);

    @Mutable
    @Accessor("y")
    void riftkingextras$setY(int y);
}
