package end.core.portal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The black portal. It is a thin pane (just like a Nether portal) that can face along X or Z.
 * Teleporting is handled in {@link end.core.portal.logic.PortalTravel}.
 */
public class EndCorePortalBlock extends Block {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	// Same shapes vanilla uses for the Nether portal.
	private static final VoxelShape X_AXIS_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
	private static final VoxelShape Z_AXIS_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

	public EndCorePortalBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_SHAPE : X_AXIS_SHAPE;
	}

	/**
	 * Vanishes when the obsidian frame is broken. Every in-plane neighbour must be obsidian or another
	 * portal block; if one isn't, this block removes itself, which triggers its neighbours to check too.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide()) {
			return;
		}

		if (!isFrameIntact(level, pos, state)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private boolean isFrameIntact(Level level, BlockPos pos, BlockState state) {
		Direction along = state.getValue(AXIS) == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction[] inPlane = {along, along.getOpposite(), Direction.UP, Direction.DOWN};

		for (Direction direction : inPlane) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));
			if (!neighbour.is(Blocks.OBSIDIAN) && !neighbour.is(this)) {
				return false;
			}
		}

		return true;
	}
}
