package end.core.portal.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import end.core.portal.ModAttachments;
import end.core.portal.ModBlocks;
import end.core.portal.ReturnPoint;
import end.core.portal.block.EndCorePortalBlock;
import end.core.portal.net.PortalFxPayload;

/**
 * Handles touching a black portal. The moment a player touches it the screen effect starts on their
 * client (black for the trip to the End, green glow for the trip back). The server then waits for the
 * sound to finish and does the teleport, and tells the client when the player has arrived so the
 * effect can fade out.
 */
public final class PortalTravel {
	/** Ticks from touching the portal to the teleport. Timed to the sound lengths (2.0s and 3.0s) plus a hair. */
	private static final int TO_END_TICKS = 42;
	private static final int TO_OVERWORLD_TICKS = 62;

	/** A trip that has started: the effect is playing and the teleport is counting down. */
	private record Pending(int kind, int ticksLeft, Direction.Axis axis, ReturnPoint returnPoint) {
		Pending tick() {
			return new Pending(kind, ticksLeft - 1, axis, returnPoint);
		}
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private PortalTravel() {
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(PortalTravel::onLevelTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.getPlayer().getUUID()));
	}

	private static void onLevelTick(ServerLevel level) {
		// Copy the list: teleporting a player to another dimension removes them from this one mid-loop.
		for (ServerPlayer player : new ArrayList<>(level.players())) {
			tickPlayer(level, player);
		}
	}

	private static void tickPlayer(ServerLevel level, ServerPlayer player) {
		Pending pending = PENDING.get(player.getUUID());

		if (pending != null) {
			tickPending(level, player, pending);
			return;
		}

		BlockPos portalPos = findPortalAt(level, player);
		if (portalPos == null || player.isSpectator() || !player.canUsePortal(false)) {
			return;
		}

		// Touched a portal: start right now.
		Direction.Axis axis = level.getBlockState(portalPos).getValue(EndCorePortalBlock.AXIS);
		boolean inEnd = level.dimension() == Level.END;

		int kind = inEnd ? PortalFxPayload.KIND_TO_OVERWORLD : PortalFxPayload.KIND_TO_END;
		int ticks = inEnd ? TO_OVERWORLD_TICKS : TO_END_TICKS;

		// Work out the return spot now, from where they actually touched the portal.
		ReturnPoint returnPoint = inEnd ? null : makeReturnPoint(level, player, portalPos, axis);

		PENDING.put(player.getUUID(), new Pending(kind, ticks, axis, returnPoint));
		sendFx(player, PortalFxPayload.ACTION_START, kind, ticks);
	}

	private static void tickPending(ServerLevel level, ServerPlayer player, Pending pending) {
		if (!player.isAlive()) {
			PENDING.remove(player.getUUID());
			sendFx(player, PortalFxPayload.ACTION_CANCEL, pending.kind(), 0);
			return;
		}

		if (pending.ticksLeft() > 1) {
			PENDING.put(player.getUUID(), pending.tick());
			return;
		}

		PENDING.remove(player.getUUID());

		boolean arrived = pending.kind() == PortalFxPayload.KIND_TO_END
				? travelToEnd(level, player, pending.returnPoint())
				: returnFromEnd(level, player);

		sendFx(player, arrived ? PortalFxPayload.ACTION_ARRIVE : PortalFxPayload.ACTION_CANCEL, pending.kind(), 0);
	}

	private static void sendFx(ServerPlayer player, int action, int kind, int ticks) {
		ServerPlayNetworking.send(player, new PortalFxPayload(action, kind, ticks));
	}

	/** Checks the block at the player's feet and head. */
	private static BlockPos findPortalAt(ServerLevel level, ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		if (level.getBlockState(feet).is(ModBlocks.END_CORE_PORTAL)) {
			return feet;
		}

		BlockPos head = feet.above();
		if (level.getBlockState(head).is(ModBlocks.END_CORE_PORTAL)) {
			return head;
		}

		return null;
	}

	// ------------------------------------------------------------------ to the End

	/** Returns true if the player was teleported. */
	private static boolean travelToEnd(ServerLevel level, ServerPlayer player, ReturnPoint returnPoint) {
		ServerLevel end = level.getServer().getLevel(Level.END);
		if (end == null) {
			player.sendSystemMessage(Component.literal("The End is not available in this world."));
			return false;
		}

		// Remember where to come back out.
		if (returnPoint != null) {
			player.setAttached(ModAttachments.RETURN_POINT, returnPoint);
		}

		// Exactly what vanilla does when you enter the End: build the 5x5 obsidian platform.
		BlockPos spawn = ServerLevel.END_SPAWN_POINT;
		EndPlatformFeature.createEndPlatform(end, spawn.below(), true);

		int floorY = findPlatformFloor(end, spawn);
		buildReturnPortal(end, spawn, floorY);

		Vec3 arrival = new Vec3(spawn.getX() + 0.5, floorY + 1, spawn.getZ() + 0.5);

		// No PLAY_PORTAL_SOUND here: the custom sound already played on the player's screen.
		player.teleport(new TeleportTransition(
				end,
				arrival,
				Vec3.ZERO,
				Direction.WEST.toYRot(), // same facing as vanilla: toward the main island, portal at your back
				0.0F,
				TeleportTransition.PLACE_PORTAL_TICKET
		));
		return true;
	}

	/**
	 * Works out a spot about 1.5 blocks out from the portal, on the side the player walked in from,
	 * facing away from the portal. That's where they'll reappear when they come back.
	 */
	private static ReturnPoint makeReturnPoint(ServerLevel level, ServerPlayer player, BlockPos portalPos, Direction.Axis axis) {
		double x = player.getX();
		double z = player.getZ();
		float yaw;

		if (axis == Direction.Axis.X) {
			// Pane runs along X, so you walk through it along Z.
			double center = portalPos.getZ() + 0.5;
			boolean south = z >= center;
			z = center + (south ? 1.5 : -1.5);
			yaw = south ? 0.0F : 180.0F;
		} else {
			// Pane runs along Z, so you walk through it along X.
			double center = portalPos.getX() + 0.5;
			boolean east = x >= center;
			x = center + (east ? 1.5 : -1.5);
			yaw = east ? -90.0F : 90.0F;
		}

		return new ReturnPoint(level.dimension(), x, player.getY(), z, yaw);
	}

	/** Finds the obsidian floor of the platform instead of assuming a height. */
	private static int findPlatformFloor(ServerLevel end, BlockPos spawn) {
		for (int y = spawn.getY() + 2; y >= spawn.getY() - 6; y--) {
			if (end.getBlockState(new BlockPos(spawn.getX(), y, spawn.getZ())).is(Blocks.OBSIDIAN)) {
				return y;
			}
		}

		return spawn.getY() - 2;
	}

	/**
	 * Builds a 4x5 obsidian frame with a 2x3 black portal on the east edge of the platform.
	 * You arrive facing west, so it ends up right behind you.
	 */
	private static void buildReturnPortal(ServerLevel end, BlockPos spawn, int floorY) {
		int x = spawn.getX() + 2;
		BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
		BlockState portal = ModBlocks.END_CORE_PORTAL.defaultBlockState().setValue(EndCorePortalBlock.AXIS, Direction.Axis.Z);

		// Pass 1: clear anything in the way. This is a separate pass on purpose: breaking a block fires
		// neighbour updates, and if a half-built portal block were next to it, that block would see a gap
		// in its frame and delete itself.
		for (int dz = -2; dz <= 1; dz++) {
			for (int dy = 0; dy <= 4; dy++) {
				BlockPos pos = new BlockPos(x, floorY + dy, spawn.getZ() + dz);
				boolean isFrame = dz == -2 || dz == 1 || dy == 0 || dy == 4;
				BlockState wanted = isFrame ? obsidian : portal;

				if (end.getBlockState(pos) != wanted && !end.getBlockState(pos).isAir()) {
					end.destroyBlock(pos, true, null);
				}
			}
		}

		// Pass 2: place it. No neighbour/shape updates, so nothing reacts to a half-built portal.
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (int dz = -2; dz <= 1; dz++) {
			for (int dy = 0; dy <= 4; dy++) {
				BlockPos pos = new BlockPos(x, floorY + dy, spawn.getZ() + dz);
				boolean isFrame = dz == -2 || dz == 1 || dy == 0 || dy == 4;
				BlockState wanted = isFrame ? obsidian : portal;

				if (end.getBlockState(pos) != wanted) {
					end.setBlock(pos, wanted, flags);
				}
			}
		}
	}

	// ------------------------------------------------------------------ back home

	/** Returns true if the player was teleported. */
	private static boolean returnFromEnd(ServerLevel end, ServerPlayer player) {
		ReturnPoint point = player.getAttached(ModAttachments.RETURN_POINT);
		ServerLevel target = point == null ? null : end.getServer().getLevel(point.dimension());

		if (point != null && target != null) {
			player.removeAttached(ModAttachments.RETURN_POINT);
			player.teleport(new TeleportTransition(
					target,
					new Vec3(point.x(), point.y(), point.z()),
					Vec3.ZERO,
					point.yaw(),
					0.0F,
					TeleportTransition.PLACE_PORTAL_TICKET
			));
			return true;
		}

		// No saved entry point (e.g. they got to this portal some other way): send them to their
		// bed / world spawn, the same place the vanilla End exit portal would.
		player.teleport(player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
		return true;
	}
}
