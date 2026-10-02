package end.core.portal;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public class ModAttachments {
	/** Saved on a player when they enter an End Core portal, read when they use the return portal. */
	public static final AttachmentType<ReturnPoint> RETURN_POINT = AttachmentRegistry.create(
			EndCorePortal.id("return_point"),
			builder -> builder.persistent(ReturnPoint.CODEC)
	);

	/** Calling this just makes sure the class (and the static field above) is loaded. */
	public static void initialize() {
	}
}
