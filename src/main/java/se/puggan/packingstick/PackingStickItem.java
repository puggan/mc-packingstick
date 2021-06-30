package se.puggan.packingstick;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
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
        inventory = player.getInventory();
        clearLists();
        registerItem(player.getStackInHand(Hand.OFF_HAND), ItemReference.Types.HAND, 0, 0);
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

        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private void clearLists() {
        stackableItems = new HashMap<>();
        emptyStacks = new Stack<>();
        fullStacks = new HashMap<>();
        uniqueItems = new Stack<>();
    }

    private ItemReference registerItem(ItemStack stack, ItemReference.Types type, int index, int storageIndex) {
        ItemReference itemReference = new ItemReference(stack, type, index, storageIndex);
        if (stack.isEmpty()) {
            emptyStacks.add(itemReference);
            return itemReference;
        }

        if (!stack.isStackable()) {
            uniqueItems.add(itemReference);
            return itemReference;
        }

        Map<String, List<ItemReference>> itemMap = itemReference.isMergeable() ? stackableItems : fullStacks;

        String itemKey = stack.getItem().getTranslationKey();
        List<ItemReference> itemList = itemMap.get(itemKey);
        if (itemList == null) {
            itemList = new Stack<>();
            itemMap.put(itemKey, itemList);
        }
        itemList.add(itemReference);

        return itemReference;
    }

    @Nullable
    private ItemReference findMergable(ItemReference itemReference, boolean split) {
        if (!itemReference.isMergeable()) {
            return null;
        }

        ItemStack stack = itemReference.getStack();
        int size = stack.getCount();
        String itemKey = stack.getItem().getTranslationKey();
        List<ItemReference> itemList = stackableItems.get(itemKey);
        if (itemList == null) {
            return null;
        }
        for (ItemReference other : itemList) {
            if (other == itemReference) {
                continue;
            }
            if (split && size > other.space()) {
                continue;
            }
            if (ItemStack.canCombine(stack, other.getStack())) {
                return other;
            }
        }

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

    private void markDirty(ItemReference ref) {
        switch (ref.getType()) {
            case INVENTORY, HOTBAR, HAND -> inventory.markDirty();
            case STORAGE -> throw new RuntimeException("Type storage not implemented");
            default -> throw new RuntimeException("Unkown type storage not implemented");
        }
    }

    private boolean loadPlayerInventory() {
        for (int i = 0; i < inventory.main.size(); ++i) {
            if (PlayerInventory.isValidHotbarIndex(i)) {
                registerItem(inventory.getStack(i), ItemReference.Types.HOTBAR, i, 0);
                continue;
            }
            ItemReference currentRef = registerItem(inventory.getStack(i), ItemReference.Types.INVENTORY, i, 0);
            ItemReference mergeable = findMergable(currentRef, false);
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
}
