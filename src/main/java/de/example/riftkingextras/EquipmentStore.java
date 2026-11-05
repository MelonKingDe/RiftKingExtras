package de.example.riftkingextras;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/** Turns an item into text and back, so the equipment can be saved in the config (all item data is kept). */
final class EquipmentStore {
    private EquipmentStore() {}

    /** The item as JSON text, or "" for an empty slot or if it cannot be saved. */
    static String encode(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return "";
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            return ItemStack.CODEC.encodeStart(ops, stack).result().map(JsonElement::toString).orElse("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The item from JSON text; an empty stack for "" or if it cannot be read. */
    static ItemStack decode(String json, HolderLookup.Provider registries) {
        if (json == null || json.isEmpty()) return ItemStack.EMPTY;
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
