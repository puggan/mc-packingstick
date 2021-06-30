package se.puggan.packingstick;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;

public class PackingStickItem extends Item {
    static Settings defaultSetting() {
        Settings settings = new Settings();
        settings.group(ItemGroup.MISC);
        settings.maxCount(1);
        return settings;
    }

    public PackingStickItem() {
        super(defaultSetting());
    }
}
