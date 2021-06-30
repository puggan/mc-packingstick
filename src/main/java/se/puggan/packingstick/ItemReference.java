package se.puggan.packingstick;

import net.minecraft.item.ItemStack;

public class ItemReference {
    public enum Types {HAND, HOTBAR, INVENTORY, STORAGE}
    private final ItemStack stack;
    private final Types type;
    private final int index;
    private final int storageIndex;
    private final boolean mergeable;
    private final int avaibleSpace;

    public ItemReference(ItemStack stack, Types type, int index, int storageIndex) {
        this.stack = stack;
        this.type = type;
        this.index = index;
        this.storageIndex = storageIndex;
        int size = stack.getCount();
        this.avaibleSpace = stack.getMaxCount() - size;
        this.mergeable = stack.isStackable() && size > 0 && avaibleSpace > 0;
    }

    public ItemStack getStack()
    {
        return this.stack;
    }

    public Types getType() {
        return type;
    }

    public boolean isMergeable()
    {
        return mergeable;
    }

    public int space()
    {
        return avaibleSpace;
    }

    public String description() {
        switch (type) {
            case HAND -> {
                return "Player offhand with " + stack.getCount() + " of " + stack.getName();
            }
            case HOTBAR -> {
                return "Player Hotbar " + index + " with " + stack.getCount() + " of " + stack.getName();
            }
            case INVENTORY -> {
                return "Player Inventory " + index + " with " + stack.getCount() + " of " + stack.getName();
            }
            case STORAGE -> {
                return "Player N/A Storage";
            }
            default -> {
                return "Player N/A ??";
            }
        }
    }
}
