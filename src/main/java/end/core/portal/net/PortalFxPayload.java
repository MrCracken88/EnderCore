package end.core.portal.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import end.core.portal.EndCorePortal;

/**
 * Tells a client to start / finish the screen effect for a portal trip.
 * The server decides when things happen; the client only draws and plays the sound.
 *
 * @param action {@link #ACTION_START}, {@link #ACTION_ARRIVE} or {@link #ACTION_CANCEL}
 * @param kind   {@link #KIND_TO_END} (black screen) or {@link #KIND_TO_OVERWORLD} (green glow)
 * @param ticks  for START: how many ticks until the server teleports the player (the effect is timed to this)
 */
public record PortalFxPayload(int action, int kind, int ticks) implements CustomPacketPayload {
	public static final int ACTION_START = 0;
	public static final int ACTION_ARRIVE = 1;
	public static final int ACTION_CANCEL = 2;

	public static final int KIND_TO_END = 0;
	public static final int KIND_TO_OVERWORLD = 1;

	public static final CustomPacketPayload.Type<PortalFxPayload> TYPE =
			new CustomPacketPayload.Type<>(EndCorePortal.id("portal_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PortalFxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PortalFxPayload::action,
			ByteBufCodecs.VAR_INT, PortalFxPayload::kind,
			ByteBufCodecs.VAR_INT, PortalFxPayload::ticks,
			PortalFxPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Must run in the common initializer so both sides know the payload. */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
