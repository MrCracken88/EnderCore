package end.core.portal;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import end.core.portal.logic.PortalTravel;
import end.core.portal.net.PortalFxPayload;

public class EndCorePortal implements ModInitializer {
	public static final String MOD_ID = "end-core-portal";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModBlocks.initialize();
		ModAttachments.initialize();
		ModSounds.initialize();
		PortalFxPayload.register();
		PortalTravel.initialize();

		LOGGER.info("End Core Portal loaded: drop an EnderCore into a Nether portal!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
