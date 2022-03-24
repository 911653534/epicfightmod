package yesman.epicfight.api.animation.types;

import javax.annotation.Nullable;

import net.minecraft.world.InteractionHand;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.animation.property.Property.AttackAnimationProperty;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.model.Model;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

public class BasicAttackAnimation extends AttackAnimation {
	public BasicAttackAnimation(float convertTime, float antic, float contact, float recovery, @Nullable Collider collider, String index, String path, Model model) {
		this(convertTime, antic, antic, contact, recovery, collider, index, path, model);
	}
	
	public BasicAttackAnimation(float convertTime, float antic, float preDelay, float contact, float recovery, @Nullable Collider collider, String index, String path, Model model) {
		super(convertTime, antic, preDelay, contact, recovery, collider, index, path, model);
		this.addProperty(AttackAnimationProperty.ROTATE_X, true);
	}
	
	public BasicAttackAnimation(float convertTime, float antic, float contact, float recovery, InteractionHand hand, @Nullable Collider collider,  String index, String path, Model model) {
		super(convertTime, antic, antic, contact, recovery, hand, collider, index, path, model);
		this.addProperty(AttackAnimationProperty.ROTATE_X, true);
	}
	
	@Override
	public void setLinkAnimation(Pose pose1, float timeModifier, LivingEntityPatch<?> entitypatch, LinkAnimation dest) {
		float extTime = Math.max(this.convertTime + timeModifier, 0);
		if (entitypatch instanceof PlayerPatch<?>) {
			PlayerPatch<?> playerdata = (PlayerPatch<?>) entitypatch;
			extTime *= (float)(this.totalTime * playerdata.getAttackSpeed());
		}
		
		extTime = Math.max(extTime - this.convertTime, 0);
		super.setLinkAnimation(pose1, extTime, entitypatch, dest);
	}
	
	@Override
	protected Vec3f getCoordVector(LivingEntityPatch<?> entitypatch, DynamicAnimation dynamicAnimation) {
		Vec3f vec3 = super.getCoordVector(entitypatch, dynamicAnimation);
		
		if (entitypatch.shouldBlockMoving()) {
			vec3.scale(0.0F);
		}
		
		return vec3;
	}
	
	@Override
	public boolean isBasicAttackAnimation() {
		return true;
	}
}