package se.puggan.packingstick;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class PackingStick implements ModInitializer {
	public static final String MOD_ID = "packingstick";
	@Override
	public void onInitialize() {
		Identifier id = new Identifier(MOD_ID, MOD_ID);
		Registry.register(Registry.ITEM, id, new PackingStickItem());
	}
}
