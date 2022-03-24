package yesman.epicfight.api.client.animation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.animation.property.Property.StaticAnimationProperty;

@OnlyIn(Dist.CLIENT)
public class ClientAnimationProperties {
	public static final StaticAnimationProperty<Layer.Priority> PRIORITY = new StaticAnimationProperty<Layer.Priority> ();
	public static final StaticAnimationProperty<JointMaskEntry> POSE_MODIFIER = new StaticAnimationProperty<JointMaskEntry> ();
}