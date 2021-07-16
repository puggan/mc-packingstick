package se.puggan.packingstick;

import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.Nullable;

public class ItemReference {
    public enum Types {HAND, HOTBAR, INVENTORY, SHULKER}
    private final ItemStack stack;
    private final Types type;
    private final int index;
    private final ItemReference parent;
    private final boolean mergeable;
    private int availableSpace;
    private DefaultedList<ItemStack> inventory;
    private final int shulkerSize = ShulkerBoxBlockEntity.field_31356;

    public ItemReference(ItemStack stack, Types type, int index, @Nullable ItemReference parent) {
        this.stack = stack;
        this.type = type;
        this.index = index;
        this.parent = parent;
        int size = stack.getCount();
        this.availableSpace = stack.getMaxCount() - size;
        this.mergeable = stack.isStackable() && size > 0 && availableSpace > 0;
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
        return availableSpace;
    }

    public String description() {
        final int count = stack.getCount();
        final String itemDecription = " with " + (count == 1 ? "a" : count) + " of " + stack.getName().getString();
        switch (type) {
            case HAND -> {
                return "Player offhand" + itemDecription;
            }
            case HOTBAR -> {
                return "Player Hotbar " + index + itemDecription;
            }
            case INVENTORY -> {
                return "Player Inventory " + index + itemDecription;
            }
            case SHULKER -> {
                return parent.description() + " inventory " + index + itemDecription;
            }
            default -> {
                return "Player N/A ??";
            }
        }
    }

    public DefaultedList<ItemStack> addInvetory()
    {
        if (inventory == null) {
            inventory = DefaultedList.of();
        }
        return inventory;
    }

    public int getIndex() {
        return index;
    }

    public ItemReference getParent() {
        return parent;
    }

    public void setShulkerSlot(int index, ItemStack stack) {
        if (inventory == null) {
            throw new RuntimeException("Not a Shulker: " + description());
        }
        if (index < 0 || index >= inventory.size()) {
            throw new IndexOutOfBoundsException("Shulker index " + index + " doesn't exists.");
        }
        if(inventory.get(index).isEmpty()) {
            availableSpace--;
        }
        inventory.set(index, stack);
        if(stack.isEmpty()) {
            availableSpace++;
        }
        saveShulker();
    }

    public int findEmptyShulkerIndex()
    {
        int size = inventory.size();
        for (int i = 0; i < size; ++i) {
            if (inventory.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public DefaultedList<ItemStack> loadShulker()
    {
        if (inventory != null) {
            return inventory;
        }
        inventory = DefaultedList.ofSize(shulkerSize, ItemStack.EMPTY);
        if (!stack.hasNbt()) {
            availableSpace = shulkerSize;
            return inventory;
        }

        NbtCompound shulkerItemTags = stack.getNbt();
        // noinspection ConstantConditions tested with hasTag()
        NbtCompound shulkerEntityTag = shulkerItemTags.getCompound(BlockItem.BLOCK_ENTITY_TAG_KEY);

        if(!shulkerEntityTag.contains("Items", NbtElement.LIST_TYPE)) {
            return inventory;
        }

        Inventories.readNbt(shulkerEntityTag, inventory);
        availableSpace = 0;
        for(ItemStack stack : inventory) {
            if(stack.isEmpty()) {
                availableSpace++;
            }
        }
        return inventory;
    }

    public void saveShulker() {
        if (inventory == null) {
            if (type == Types.SHULKER && parent != null) {
                parent.saveShulker();
                return;
            }
            throw new RuntimeException("Can't save missing inventory from " + description());
        }
        NbtCompound shulkerItemTags = stack.getOrCreateNbt();
        NbtCompound shulkerEntityTag = shulkerItemTags.getCompound(BlockItem.BLOCK_ENTITY_TAG_KEY);
        shulkerEntityTag.remove("Items");
        Inventories.writeNbt(shulkerEntityTag, inventory, false);
        shulkerItemTags.put(BlockItem.BLOCK_ENTITY_TAG_KEY, shulkerEntityTag);
    }
}
