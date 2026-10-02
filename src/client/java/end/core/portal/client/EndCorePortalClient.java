package end.core.portal.client;

import net.fabricmc.api.ClientModInitializer;

public class EndCorePortalClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PortalFxClient.initialize();
	}
}
