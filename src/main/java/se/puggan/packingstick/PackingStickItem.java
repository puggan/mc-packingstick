package se.puggan.packingstick;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class PackingStickItem extends Item {
    private Map<String, List<ItemReference>> stackableItems;
    private Map<String, List<ItemReference>> fullStacks;
    private List<ItemReference> emptyStacks;
    private List<ItemReference> shulkerItems;
    private List<ItemReference> uniqueItems;

    private PlayerInventory inventory;

    static Settings defaultSetting() {
        Settings settings = new Settings();
        settings.group(ItemGroup.MISC);
        settings.maxCount(1);
        return settings;
    }

    public PackingStickItem() {
        super(defaultSetting());
        clearLists();
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }
        inventory = player.getInventory();
        clearLists();
        registerItem(player.getStackInHand(Hand.OFF_HAND), ItemReference.Types.HAND, 0, null);
        if (loadPlayerInventory()) {
            return TypedActionResult.success(player.getStackInHand(hand), true);
        }

        if (tryMerge(true, false)) {
            return TypedActionResult.success(player.getStackInHand(hand), true);
        }

        if (emptyStacks.isEmpty()) {
            if (tryMerge(false, true)) {
                return TypedActionResult.success(player.getStackInHand(hand), true);
            }
        }

        if (fillHotbar()) {
            return TypedActionResult.success(player.getStackInHand(hand), true);
        }

        if (shulkerItems.size() > 0) {
            for (ItemReference shulkerRef : shulkerItems) {
                if (registerShulker(shulkerRef)) {
                    return TypedActionResult.success(player.getStackInHand(hand), true);
                }
            }
            ItemReference emptyShulker = null;
            for (ItemReference shulkerRef : shulkerItems) {
                System.out.println("Shulker space: " + shulkerRef.space() + " in: " + shulkerRef.description());
                if (shulkerRef.space() > 0) {
                    emptyShulker = shulkerRef;
                    break;
                }
            }

            if (emptyShulker != null) {
                System.out.println("Shulker with empty spaces found at: " + emptyShulker.description());
                for(List<ItemReference> itemList : fullStacks.values()) {
                    for (ItemReference fullRef : itemList) {
                        if (fullRef.getType() == ItemReference.Types.INVENTORY) {
                            System.out.println("Movind " + fullRef.description() + " to shulkerbox.");
                            move(emptyShulker, emptyShulker.findEmptyShulkerIndex(), fullRef);
                            return TypedActionResult.success(player.getStackInHand(hand), true);
                        }
                    }
                }
            }
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private void clearLists() {
        System.out.println("Restart search -- Emptying all lists");
        stackableItems = new HashMap<>();
        emptyStacks = new Stack<>();
        shulkerItems = new Stack<>();
        fullStacks = new HashMap<>();
        uniqueItems = new Stack<>();
    }

    private ItemReference registerItem(ItemStack stack, ItemReference.Types type, int index, @Nullable ItemReference parent) {
        ItemReference itemReference = new ItemReference(stack, type, index, parent);
        if (stack.isEmpty()) {
            emptyStacks.add(itemReference);
            return itemReference;
        }

        if (!stack.isStackable()) {
            Item item = stack.getItem();
            if (item instanceof BlockItem bItem && bItem.getBlock() instanceof ShulkerBoxBlock) {
                shulkerItems.add(itemReference);
                System.out.println("Shulker found: " + itemReference.description());
            } else {
                uniqueItems.add(itemReference);
            }
            return itemReference;
        }

        Map<String, List<ItemReference>> itemMap = itemReference.isMergeable() ? stackableItems : fullStacks;

        String itemKey = stack.getItem().getTranslationKey();
        List<ItemReference> itemList;
        if (itemMap.containsKey(itemKey)) {
            itemList = itemMap.get(itemKey);
        } else {
            itemList = new Stack<>();
            itemMap.put(itemKey, itemList);
        }
        itemList.add(itemReference);

        return itemReference;
    }

    @Nullable
    private ItemReference findMergeable(ItemReference itemReference, boolean split, boolean toStorage) {
        if (!itemReference.isMergeable()) {
            return null;
        }

        ItemStack stack = itemReference.getStack();
        int size = stack.getCount();
        String itemKey = stack.getItem().getTranslationKey();
        List<ItemReference> itemList = stackableItems.get(itemKey);
        if (itemList != null) {
            for (ItemReference other : itemList) {
                if (other == itemReference) {
                    continue;
                }
                if (toStorage) {
                    int space = other.space();
                    boolean ok = false;
                    switch (other.getType()) {
                        case HOTBAR -> ok = size > space;
                        case INVENTORY -> ok = true;
                    }
                    if (!ok) {
                        continue;
                    }
                }
                if (!split && size > other.space()) {
                    continue;
                }
                if (ItemStack.canCombine(stack, other.getStack())) {
                    return other;
                }
            }
        }

        if(!split) {
            return null;
        }

        if (!toStorage) {
            return null;
        }
        System.out.println("No merge found for " + itemReference.description());

        itemList = fullStacks.get(itemKey);
        if (itemList == null) {
            return null;
        }

        for (ItemReference other : itemList) {
            if (other == itemReference) {
                continue;
            }
            boolean ok = false;
            switch (other.getType()) {
                case HOTBAR, INVENTORY -> ok = true;
            }
            if (!ok) {
                continue;
            }
            if (ItemStack.canCombine(stack, other.getStack())) {
                return other;
            }
        }

        System.out.println("No split found for " + itemReference.description());
        return null;
    }

    private boolean merge(ItemReference to, ItemReference from) {
        ItemStack toStack = to.getStack();
        ItemStack fromStack = from.getStack();
        if (!ItemStack.canCombine(fromStack, toStack)) {
            return false;
        }

        int space = toStack.getMaxCount() - toStack.getCount();
        int available = fromStack.getCount();
        int moved = Math.min(available, space);

        fromStack.decrement(moved);
        toStack.increment(moved);
        markDirty(from);
        markDirty(to);
        return true;
    }

    private boolean move(ItemReference shulkerReference, int index, ItemReference from) {
        ItemStack stack = from.getStack();
        shulkerReference.setShulkerSlot(index, stack);
        switch (from.getType()) {
            case INVENTORY, HOTBAR -> inventory.setStack(from.getIndex(), ItemStack.EMPTY);
            case HAND -> inventory.player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            case SHULKER -> {
                from.setShulkerSlot(index, ItemStack.EMPTY);
                markDirty(from.getParent());
            }
            default -> throw new RuntimeException("Unknown type");
        }
        markDirty(from);
        return true;
    }

    private void markDirty(ItemReference ref) {
        switch (ref.getType()) {
            case INVENTORY, HOTBAR, HAND -> inventory.markDirty();
            case SHULKER -> {
                ref.saveShulker();
                markDirty(ref.getParent());
            }
            default -> throw new RuntimeException("Unkown type storage not implemented");
        }
    }

    private boolean loadPlayerInventory() {
        for (int i = 0; i < inventory.main.size(); ++i) {
            if (PlayerInventory.isValidHotbarIndex(i)) {
                registerItem(inventory.getStack(i), ItemReference.Types.HOTBAR, i, null);
                continue;
            }
            ItemReference currentRef = registerItem(inventory.getStack(i), ItemReference.Types.INVENTORY, i, null);
            ItemReference mergeable = findMergeable(currentRef, false, false);
            if (mergeable == null) {
                continue;
            }
            if (merge(mergeable, currentRef)) {
                // Merge done, abort
                return true;
            }
        }
        // No merge done
        return false;
    }

    private boolean tryMerge(boolean split, boolean hotbar) {
        for (List<ItemReference> itemList : stackableItems.values()) {
            if (itemList.size() < 2) {
                continue;
            }
            List<ItemReference> tested = new Stack<>();

            for (ItemReference newRef : itemList) {
                ItemReference.Types type = newRef.getType();
                if (type == ItemReference.Types.HAND) {
                    tested.add(newRef);
                    continue;
                }
                if (!hotbar && type == ItemReference.Types.HOTBAR) {
                    tested.add(newRef);
                    continue;
                }
                ItemStack stack = newRef.getStack();
                int size = stack.getCount();
                for (ItemReference otherRef : tested) {
                    if (otherRef == newRef) {
                        continue;
                    }
                    if (split && size > otherRef.space()) {
                        continue;
                    }
                    if (ItemStack.canCombine(stack, otherRef.getStack())) {
                        return merge(otherRef, newRef);
                    }
                }
            }
        }
        return false;
    }

    private boolean fillHotbar() {
        for (String itemKey : stackableItems.keySet()) {
            if (!fullStacks.containsKey(itemKey)) {
                continue;
            }
            List<ItemReference> fullStackList = fullStacks.get(itemKey);
            for (ItemReference itemReference : stackableItems.get(itemKey)) {
                ItemReference.Types type = itemReference.getType();
                if (type != ItemReference.Types.HAND && type != ItemReference.Types.HOTBAR) {
                    continue;
                }

                ItemStack stack = itemReference.getStack();

                for (ItemReference fullRef : fullStackList) {
                    if (fullRef == itemReference) {
                        continue;
                    }

                    ItemReference.Types fullType = fullRef.getType();
                    if (fullType == type || fullType == ItemReference.Types.HAND) {
                        continue;
                    }

                    ItemStack fullStack = fullRef.getStack();
                    if (fullStack == stack) {
                        continue;
                    }
                    if (ItemStack.canCombine(stack, fullStack)) {
                        return merge(itemReference, fullRef);
                    }
                }
            }
        }
        return false;
    }

    private boolean registerShulker(ItemReference shulkerReference) {
        System.out.println("Register Shulker: " + shulkerReference.description());
        DefaultedList<ItemStack> shulkerInventory = shulkerReference.loadShulker();
        int inventorySize = shulkerInventory.size();
        System.out.println("Shulker Items: " + inventorySize);

        int firstEmpty = -1;
        for (int i = 0; i < inventorySize; ++i) {
            ItemStack stack = shulkerInventory.get(i);
            if (stack.isEmpty()) {
                if (firstEmpty == -1) {
                    firstEmpty = i;
                    System.out.println("Shulker Item " + i + ": (Empty)");
                }
                continue;
            }
            System.out.println("Shulker Item " + i + ": " + stack.getName().getString());
            ItemReference stackRef = registerItem(stack, ItemReference.Types.SHULKER, i, shulkerReference);
            if (!stackRef.isMergeable()) {
                continue;
            }
            ItemReference otherRef = findMergeable(stackRef, true, true);
            if (otherRef == null) {
                continue;
            }
            return merge(stackRef, otherRef);
        }

        if (firstEmpty < 0) {
            return false;
        }

        List<String> tested = new Stack<>();
        for (int i = 0; i < inventorySize; ++i) {
            ItemStack stack = shulkerInventory.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            String itemKey = stack.getItem().getTranslationKey();
            if (tested.contains(itemKey)) {
                continue;
            }
            tested.add(itemKey);
            if (fullStacks.containsKey(itemKey)) {
                for (ItemReference other : fullStacks.get(itemKey)) {
                    if (other.getType() != ItemReference.Types.INVENTORY) {
                        continue;
                    }
                    return move(shulkerReference, firstEmpty, other);
                }
            }
        }

        return false;
    }
}
