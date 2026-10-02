package end.core.portal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import end.core.portal.EndCorePortal;
import end.core.portal.ModSounds;
import end.core.portal.net.PortalFxPayload;

/**
 * The screen effect for portal trips, drawn over everything (HUD and loading screens):
 * <ul>
 *   <li>to the End: the screen goes black, then fades back in once you've arrived on the platform</li>
 *   <li>back to the Overworld: a faint green glow creeps in from the edges while you load, then
 *       recedes once you've arrived</li>
 * </ul>
 * The server tells us when to start and when the player has arrived; everything here is cosmetic.
 */
public final class PortalFxClient {
	// ---- tweak these -------------------------------------------------------------------------

	/** How fast the screen goes black after touching the portal (to the End). */
	private static final long FADE_TO_BLACK_MS = 300;

	/** How long the effect takes to fade away after you arrive. */
	private static final long FADE_IN_MS = 2500;

	/** Colour of the glow on the way back (RGB). */
	private static final int GLOW_RGB = 0x33FF66;

	/** How strong the glow is right at the screen edge when fully "arrived" (0 to 1). Keep it low for "faint". */
	private static final float GLOW_EDGE_ALPHA = 0.55F;

	/** A very light green tint over the whole screen at full strength (0 to 1). */
	private static final float GLOW_WASH_ALPHA = 0.12F;

	// ---- state -------------------------------------------------------------------------------

	private static final long GIVE_UP_MS = 8000;        // never get stuck if the server never answers
	private static final long LOADING_WAIT_MS = 4000;   // how long we'll wait for a loading screen to close

	private static boolean active;
	private static int kind;
	private static long startMs;
	private static long holdMs;
	private static boolean arrived;
	private static long arrivedMs;
	private static float levelAtArrival;
	private static long fadeInStartMs;

	private PortalFxClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(PortalFxPayload.TYPE, (payload, context) -> onPayload(payload));

		// In the world with no screen open: draw on top of the whole HUD.
		HudElementRegistry.addLast(EndCorePortal.id("portal_fx"), (graphics, tickCounter) -> {
			if (Minecraft.getInstance().gui.screen() == null) {
				render(graphics);
			}
		});

		// Loading screens (and any other screen) cover the HUD, so draw on top of those too.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> render(graphics)));
	}

	// ---- messages from the server ------------------------------------------------------------

	private static void onPayload(PortalFxPayload payload) {
		long now = System.currentTimeMillis();

		if (payload.action() == PortalFxPayload.ACTION_START) {
			active = true;
			kind = payload.kind();
			startMs = now;
			holdMs = Math.max(1, payload.ticks() * 50L);
			arrived = false;
			fadeInStartMs = -1;

			SoundEvent sound = kind == PortalFxPayload.KIND_TO_END ? ModSounds.PORTAL_TO_END : ModSounds.PORTAL_TO_OVERWORLD;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
			return;
		}

		// ARRIVE or CANCEL: either way, start fading out of the effect.
		if (active && !arrived) {
			markArrived(now);
		}
	}

	private static void markArrived(long now) {
		levelAtArrival = levelBeforeArrival(now);
		arrived = true;
		arrivedMs = now;
		fadeInStartMs = -1;
	}

	// ---- drawing -----------------------------------------------------------------------------

	/** 0 to 1: how strong the effect is while we're still waiting for the teleport. */
	private static float levelBeforeArrival(long now) {
		long rampMs = kind == PortalFxPayload.KIND_TO_END ? FADE_TO_BLACK_MS : holdMs;
		return clamp01((now - startMs) / (float) rampMs);
	}

	public static void render(GuiGraphicsExtractor graphics) {
		if (!active) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			active = false; // left the world
			return;
		}

		long now = System.currentTimeMillis();

		if (!arrived && now - startMs > holdMs + GIVE_UP_MS) {
			markArrived(now);
		}

		float level;
		if (!arrived) {
			level = levelBeforeArrival(now);
		} else {
			// Don't start fading in while the "loading terrain" screen is still up, or you'd miss it.
			if (fadeInStartMs < 0 && (mc.gui.screen() == null || now - arrivedMs > LOADING_WAIT_MS)) {
				fadeInStartMs = now;
			}

			if (fadeInStartMs < 0) {
				level = levelAtArrival;
			} else {
				float fade = clamp01((now - fadeInStartMs) / (float) FADE_IN_MS);
				if (fade >= 1.0F) {
					active = false;
					return;
				}
				level = levelAtArrival * (1.0F - fade);
			}
		}

		int width = mc.getWindow().getGuiScaledWidth();
		int height = mc.getWindow().getGuiScaledHeight();

		if (kind == PortalFxPayload.KIND_TO_END) {
			drawBlack(graphics, width, height, level);
		} else {
			drawGlow(graphics, width, height, level, now);
		}
	}

	private static void drawBlack(GuiGraphicsExtractor graphics, int width, int height, float level) {
		int color = argb(level, 0x000000);
		if ((color >>> 24) != 0) {
			graphics.fill(0, 0, width, height, color);
		}
	}

	/**
	 * A green vignette. As {@code level} goes from 0 to 1 the glow gets stronger AND creeps further in
	 * from the edges towards the middle ("gets closer"). Going back down 1 to 0 it recedes again.
	 */
	private static void drawGlow(GuiGraphicsExtractor graphics, int width, int height, float level, long now) {
		if (level <= 0.0F) {
			return;
		}

		// A slow, gentle pulse so it feels alive.
		float pulse = 0.92F + 0.08F * (float) Math.sin(now / 180.0);

		// Whole-screen light tint.
		int wash = argb(GLOW_WASH_ALPHA * level, GLOW_RGB);
		if ((wash >>> 24) != 0) {
			graphics.fill(0, 0, width, height, wash);
		}

		// Vignette: concentric rectangular rings from the edge inwards, each fainter than the last.
		float halfMin = Math.min(width, height) / 2.0F;
		float depth = halfMin * (0.15F + 0.85F * level);
		int bands = 48;
		float step = depth / bands;

		for (int i = 0; i < bands; i++) {
			int in0 = Math.round(i * step);
			int in1 = Math.round((i + 1) * step);
			if (in1 <= in0) {
				continue;
			}

			float t = (i + 0.5F) / bands;                       // 0 at the edge, 1 at the deepest point
			float alpha = GLOW_EDGE_ALPHA * level * pulse * (1.0F - t) * (1.0F - t);
			int color = argb(alpha, GLOW_RGB);
			if ((color >>> 24) == 0) {
				continue;
			}

			graphics.fill(in0, in0, width - in0, in1, color);                 // top
			graphics.fill(in0, height - in1, width - in0, height - in0, color); // bottom
			graphics.fill(in0, in1, in1, height - in1, color);                // left
			graphics.fill(width - in1, in1, width - in0, height - in1, color); // right
		}
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.round(clamp01(alpha) * 255.0F);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static float clamp01(float value) {
		return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
	}
}
