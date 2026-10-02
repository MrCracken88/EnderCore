package end.core.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where a player should come back out when they use the return portal in the End.
 * It is saved on the player, so it survives world reloads and server restarts.
 */
public record ReturnPoint(ResourceKey<Level> dimension, double x, double y, double z, float yaw) {
	public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x),
			Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
			Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z),
			Codec.FLOAT.fieldOf("yaw").forGetter(ReturnPoint::yaw)
	).apply(instance, ReturnPoint::new));
}
