package end.core.portal;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import end.core.portal.block.EndCorePortalBlock;

public class ModBlocks {
	/** The black portal. It has no item on purpose: it can only be created by dropping an EnderCore into a Nether portal. */
	public static final Block END_CORE_PORTAL = register(
			"end_core_portal",
			EndCorePortalBlock::new,
			BlockBehaviour.Properties.of()
					.noCollision()
					.noOcclusion()
					.noLootTable()
					.strength(-1.0F)
					.sound(SoundType.GLASS)
					.lightLevel(state -> 11)
					.pushReaction(PushReaction.BLOCK)
	);

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, EndCorePortal.id(name));
		Block block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	public static void initialize() {
	}
}
