package end.core.portal;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

public class ModItems {
	public static final ResourceKey<Item> ENDER_CORE_ID =
			ResourceKey.create(Registries.ITEM, EndCorePortal.id("ender_core"));

	public static final Item ENDER_CORE = register(
			ENDER_CORE_ID,
			Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
	);

	private static Item register(ResourceKey<Item> key, Function<Item.Properties, Item> factory, Item.Properties properties) {
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(tab -> tab.accept(ENDER_CORE));
	}
}
