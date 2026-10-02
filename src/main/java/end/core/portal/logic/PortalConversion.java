package end.core.portal.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import end.core.portal.ModBlocks;
import end.core.portal.ModItems;
import end.core.portal.block.EndCorePortalBlock;

/**
 * Turns a Nether portal into a black End Core portal when an EnderCore is dropped into it.
 * Called every tick for every dropped item from {@link end.core.portal.mixin.ItemEntityMixin}.
 */
public final class PortalConversion {
	/** Biggest vanilla portal is 21x21 = 441 portal blocks; this is just a safety cap. */
	private static final int MAX_PORTAL_BLOCKS = 600;

	private PortalConversion() {
	}

	public static void tryConvert(ItemEntity itemEntity) {
		// Cheapest checks first, because this runs for every item entity every tick.
		if (!(itemEntity.level() instanceof ServerLevel level)) {
			return;
		}

		ItemStack stack = itemEntity.getItem();
		if (!stack.is(ModItems.ENDER_CORE)) {
			return;
		}

		BlockPos pos = itemEntity.blockPosition();
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.NETHER_PORTAL)) {
			return;
		}

		Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
		int converted = convertPortal(level, pos, axis);
		if (converted == 0) {
			return;
		}

		// Use up exactly one EnderCore.
		if (stack.getCount() > 1) {
			itemEntity.setItem(stack.copyWithCount(stack.getCount() - 1));
		} else {
			itemEntity.discard();
		}

		level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.PORTAL, itemEntity.getX(), itemEntity.getY() + 0.5, itemEntity.getZ(), 60, 0.5, 0.8, 0.5, 0.3);
	}

	/**
	 * Finds every Nether portal block connected to {@code start} in the same plane and replaces
	 * them all with the black portal. Returns how many blocks were converted.
	 */
	private static int convertPortal(ServerLevel level, BlockPos start, Direction.Axis axis) {
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction[] inPlane = {along, along.getOpposite(), Direction.UP, Direction.DOWN};

		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> toConvert = new ArrayList<>();

		seen.add(start);
		queue.add(start);

		while (!queue.isEmpty() && toConvert.size() < MAX_PORTAL_BLOCKS) {
			BlockPos current = queue.poll();
			BlockState state = level.getBlockState(current);

			if (!state.is(Blocks.NETHER_PORTAL) || state.getValue(BlockStateProperties.HORIZONTAL_AXIS) != axis) {
				continue;
			}

			toConvert.add(current);

			for (Direction direction : inPlane) {
				BlockPos next = current.relative(direction);
				if (seen.add(next)) {
					queue.add(next);
				}
			}
		}

		BlockState replacement = ModBlocks.END_CORE_PORTAL.defaultBlockState().setValue(EndCorePortalBlock.AXIS, axis);

		// UPDATE_KNOWN_SHAPE is the important flag here. Without it, every block we replace tells its
		// neighbours to re-check their shape. The Nether portal blocks we haven't converted yet react by
		// deleting themselves (the portal is "broken" now), which sends a neighbour update to the black
		// portal blocks we already placed. Those see an air gap in their frame and delete themselves too,
		// which is what left a hole in the portal. With the flag set, nobody reacts mid-conversion.
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (BlockPos pos : toConvert) {
			level.setBlock(pos, replacement, flags);
		}

		return toConvert.size();
	}
}
