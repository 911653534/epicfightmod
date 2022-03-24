package yesman.epicfight.api.animation;

import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public abstract class Animator {
	protected Pose prevPose = new Pose();
	protected Pose currentPose = new Pose();
	protected LivingEntityPatch<?> entitypatch;
	public abstract void playAnimation(StaticAnimation nextAnimation, float convertTimeModifier);
	public abstract void playAnimationInstantly(StaticAnimation nextAnimation);
	public abstract void update();
	/** Standby until the current animation is completely end. Mostly used for attaching two animations having the same last & first pose **/
	public abstract void reserveAnimation(StaticAnimation nextAnimation);
	public abstract EntityState getEntityState();
	public abstract AnimationPlayer getPlayerFor(DynamicAnimation playingAnimation);
	public abstract void init();
	public abstract void updatePose();
	
	public final void playAnimation(int namespaceId, int id, float convertTimeModifier) {
		this.playAnimation(EpicFightMod.getInstance().animationManager.findAnimation(namespaceId, id), convertTimeModifier);
	}
	
	public final void playAnimationInstantly(int namespaceId, int id) {
		this.playAnimationInstantly(EpicFightMod.getInstance().animationManager.findAnimation(namespaceId, id));
	}
	
	public Pose getPose(float partialTicks) {
		return Pose.interpolatePose(this.prevPose, this.currentPose, partialTicks);
	}
	
	public boolean isReverse() {
		return false;
	}
	
	public void playDeathAnimation() {
		this.playAnimation(Animations.BIPED_DEATH, 0);
	}
	
	/** Get binded position of joint **/
	public static OpenMatrix4f getBindedJointTransformByName(Pose pose, Armature armature, String jointName) {
		return getBindedJointTransformByIndex(pose, armature, armature.searchPathIndex(jointName));
	}
	
	/** Get binded position of joint **/
	public static OpenMatrix4f getBindedJointTransformByIndex(Pose pose, Armature armature, int pathIndex) {
		armature.initializeTransform();
		return getBindedJointTransformByIndexInternal(pose, armature.getJointHierarcy(), new OpenMatrix4f(), pathIndex);
	}
	
	private static OpenMatrix4f getBindedJointTransformByIndexInternal(Pose pose, Joint joint, OpenMatrix4f parentTransform, int pathIndex) {
		JointTransform jt = pose.getTransformByName(joint.getName());
		OpenMatrix4f result = jt.getAnimationBindedMatrix(joint, parentTransform);
		int nextIndex = pathIndex % 10;
		return nextIndex > 0 ? getBindedJointTransformByIndexInternal(pose, joint.getSubJoints().get(nextIndex - 1), result, pathIndex / 10) : result;
	}
}