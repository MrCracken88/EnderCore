package end.core.portal.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import end.core.portal.logic.PortalConversion;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	// RETURN (not TAIL) so this runs on every exit path of tick().
	@Inject(method = "tick", at = @At("RETURN"))
	private void endcoreportal$checkForPortal(CallbackInfo info) {
		PortalConversion.tryConvert((ItemEntity) (Object) this);
	}
}
