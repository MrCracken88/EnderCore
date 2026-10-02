package end.core.portal;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
	/** Plays when you touch the black portal and get taken to the End (assets/.../sounds/portal_to_end.ogg). */
	public static final SoundEvent PORTAL_TO_END = register("portal_to_end");

	/** Plays when you touch the return portal in the End (assets/.../sounds/portal_to_overworld.ogg). */
	public static final SoundEvent PORTAL_TO_OVERWORLD = register("portal_to_overworld");

	private static SoundEvent register(String name) {
		Identifier id = EndCorePortal.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
	}
}
