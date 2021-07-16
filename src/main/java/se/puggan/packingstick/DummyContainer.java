package se.puggan.packingstick;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.world.World;

import java.util.Optional;

public class DummyContainer extends ScreenHandler {
    protected World world;
    protected RecipeManager recipeManager;
    protected CraftingInventory dummyInventory;

    protected DummyContainer(World world) {
        super(ScreenHandlerType.CRAFTING, 0);
        this.world = world;
        recipeManager = world.getRecipeManager();
        dummyInventory = new CraftingInventory(this, 3, 3);
    }

    public void fill(Item item, int count)
    {
        for(int i = 0; i < 9; i++) {
            dummyInventory.setStack(i, i < count ? new ItemStack(item, 1) : ItemStack.EMPTY);
        }

        /* if 4, move the 3th so we get a square instead of an L-shape */
        if (count == 4) {
            dummyInventory.setStack(4,  new ItemStack(item, 1));
            dummyInventory.setStack(2,  ItemStack.EMPTY);
        }
    }

    public Optional<CraftingRecipe> getRecipe()
    {
        return recipeManager.getFirstMatch(RecipeType.CRAFTING, dummyInventory, world);
    }

    public Optional<CraftingRecipe> getRecipe(Item item, int count)
    {
        fill(item, count);
        return getRecipe();
    }

    @Override
    public boolean canUse(PlayerEntity playerIn) {
        return false;
    }

    @Override
    public void onContentChanged(Inventory inventoryIn) {
    }
}
