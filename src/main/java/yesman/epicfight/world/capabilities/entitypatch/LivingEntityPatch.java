package yesman.epicfight.world.capabilities.entitypatch;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.ServerAnimator;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.model.Model;
import yesman.epicfight.api.utils.game.AttackResult;
import yesman.epicfight.api.utils.game.ExtendedDamageSource;
import yesman.epicfight.api.utils.game.ExtendedDamageSource.StunType;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.Models;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPPlayAnimation;
import yesman.epicfight.particle.HitParticleType;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.entity.eventlistener.HurtEventPre;

public abstract class LivingEntityPatch<T extends LivingEntity> extends EntityPatch<T> {
	public static final EntityDataAccessor<Float> STUN_SHIELD = new EntityDataAccessor<Float> (252, EntityDataSerializers.FLOAT);
	public static final EntityDataAccessor<Float> STAMINA = new EntityDataAccessor<Float> (253, EntityDataSerializers.FLOAT);
	
	private float stunTimeReduction;
	protected EntityState state = EntityState.FREE;
	protected Animator animator;
	public LivingMotion currentMotion = LivingMotion.IDLE;
	public LivingMotion currentCompositeMotion = LivingMotion.IDLE;
	public List<LivingEntity> currentlyAttackedEntity;
	protected Vec3 lastAttackPosition;
	
	@Override
	public void onConstructed(T entityIn) {
		super.onConstructed(entityIn);
		this.animator = EpicFightMod.getAnimator(this);
		this.animator.init();
		this.currentlyAttackedEntity = new ArrayList<LivingEntity>();
		this.original.getEntityData().define(STUN_SHIELD, Float.valueOf(0.0F));
	}
	
	@Override
	public void onJoinWorld(T entityIn, EntityJoinWorldEvent event) {
		super.onJoinWorld(entityIn, event);
		this.initAttributes();
	}
	
	@OnlyIn(Dist.CLIENT)
	public abstract void initAnimator(ClientAnimator animatorClient);
	public abstract void updateMotion(boolean considerInaction);
	public abstract <M extends Model> M getEntityModel(Models<M> modelDB);
	
	protected void initAttributes() {
		this.original.getAttribute(EpicFightAttributes.WEIGHT.get()).setBaseValue(this.original.getAttribute(Attributes.MAX_HEALTH).getBaseValue() * 2.0D);
		this.original.getAttribute(EpicFightAttributes.MAX_STRIKES.get()).setBaseValue(1.0D);
		this.original.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()).setBaseValue(0.0D);
		this.original.getAttribute(EpicFightAttributes.IMPACT.get()).setBaseValue(0.5D);
	}
	
	@Override
	protected void clientTick(LivingUpdateEvent event) {
		ClientAnimator animator = this.getClientAnimator();
		this.updateMotion(true);
		animator.update();
	}
	
	@Override
	protected void serverTick(LivingUpdateEvent event) {
		if (this.stunTimeReduction > 0.0F) {
			float stunArmor = this.getStunArmor();
			this.stunTimeReduction = Math.max(0.0F, this.stunTimeReduction - 0.03F * (1 - stunArmor / (7.5F + stunArmor)));
		}
		
		this.animator.update();
	}
	
	@Override
	public void tick(LivingUpdateEvent event) {
		this.updateEntityState();
		
		if (this.isLogicalClient()) {
			this.clientTick(event);
		} else {
			this.serverTick(event);
		}
		
		if (this.original.deathTime == 19) {
			this.aboutToDeath();
		}
	}
	
	public void onDeath() {
		this.getAnimator().playDeathAnimation();
	}
	
	public void updateEntityState() {
		this.state = this.animator.getEntityState();
	}
	
	protected final void commonBipedCreatureAnimatorInit(ClientAnimator animatorClient) {
		animatorClient.addLivingAnimation(LivingMotion.IDLE, Animations.BIPED_IDLE);
		animatorClient.addLivingAnimation(LivingMotion.WALK, Animations.BIPED_WALK);
		animatorClient.addLivingAnimation(LivingMotion.FALL, Animations.BIPED_FALL);
		animatorClient.addLivingAnimation(LivingMotion.MOUNT, Animations.BIPED_MOUNT);
		animatorClient.addLivingAnimation(LivingMotion.DEATH, Animations.BIPED_DEATH);
	}
	
	protected final void humanoidEntityUpdateMotion(boolean considerInaction) {
		if (this.state.inaction() && considerInaction) {
			currentMotion = LivingMotion.INACTION;
		} else {
			if (this.original.getHealth() <= 0.0F) {
				currentMotion = LivingMotion.DEATH;
			} else if (original.getVehicle() != null) {
				currentMotion = LivingMotion.MOUNT;
			} else {
				if (this.original.getDeltaMovement().y < -0.55F)
					currentMotion = LivingMotion.FALL;
				else if (original.animationSpeed > 0.01F)
					currentMotion = LivingMotion.WALK;
				else
					currentMotion = LivingMotion.IDLE;
			}
		}
		
		this.currentCompositeMotion = this.currentMotion;
	}
	
	protected final void humanoidRangedEntityUpdateMotion(boolean considerInaction) {
		this.humanoidEntityUpdateMotion(considerInaction);
		UseAnim useAction = this.original.getItemInHand(this.original.getUsedItemHand()).getUseAnimation();
		
		if (this.original.isUsingItem()) {
			if (useAction == UseAnim.CROSSBOW)
				currentCompositeMotion = LivingMotion.RELOAD;
			else
				currentCompositeMotion = LivingMotion.AIM;
		} else {
			if (this.getClientAnimator().getCompositeLayer(Layer.Priority.MIDDLE).animationPlayer.getPlay().isReboundAnimation())
				currentCompositeMotion = LivingMotion.NONE;
		}
		
		if (CrossbowItem.isCharged(this.original.getMainHandItem()))
			currentCompositeMotion = LivingMotion.AIM;
		else if (this.getClientAnimator().isAiming() && currentCompositeMotion != LivingMotion.AIM)
			this.playReboundAnimation();
	}
	
	public void cancelUsingItem() {
		this.original.stopUsingItem();
		net.minecraftforge.event.ForgeEventFactory.onUseItemStop(this.original, this.original.getUseItem(), this.original.getUseItemRemainingTicks());
	}
	
	public CapabilityItem getHeldItemCapability(InteractionHand hand) {
		return EpicFightCapabilities.getItemStackCapability(this.original.getItemInHand(hand));
	}
	
	public CapabilityItem getAdvancedHeldItemCapability(InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND) {
			return getHeldItemCapability(hand);
		} else {
			return this.isValidOffhandItem() ? this.getHeldItemCapability(hand) : CapabilityItem.EMPTY;
		}
	}
	
	public ExtendedDamageSource getDamageSource(StunType stunType, StaticAnimation animation, InteractionHand hand) {
		return ExtendedDamageSource.causeMobDamage(this.original, stunType, animation);
	}
	
	public float calculateDamageTo(@Nullable Entity targetEntity, @Nullable ExtendedDamageSource source, InteractionHand hand) {
		float damage = 0;
		
		if (hand == InteractionHand.MAIN_HAND) {
			damage = (float) this.original.getAttributeValue(Attributes.ATTACK_DAMAGE);
		} else {
			damage = this.isValidOffhandItem() ? (float) this.original.getAttributeValue(EpicFightAttributes.OFFHAND_ATTACK_DAMAGE.get()) : (float) this.original.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
		}
		
		damage += EnchantmentHelper.getDamageBonus(this.getValidItemInHand(hand), (targetEntity instanceof LivingEntity) ? ((LivingEntity)targetEntity).getMobType() : MobType.UNDEFINED);
		
		return damage;
	}
	
	public AttackResult tryHurt(DamageSource damageSource, float amount) {
		if (this.getEntityState().invulnerableTo(damageSource)) {
			return new AttackResult(AttackResult.ResultType.FAILED, amount);
		}
		
		return new AttackResult(AttackResult.ResultType.SUCCESS, amount);
	}
	
	public AttackResult harmEntity(Entity target, ExtendedDamageSource damagesource, float amount) {
		LivingEntityPatch<?> livingpatch = (LivingEntityPatch<?>)target.getCapability(EpicFightCapabilities.CAPABILITY_ENTITY).orElse(null);
		AttackResult result = (livingpatch != null) ? livingpatch.tryHurt((DamageSource)damagesource, amount) : new AttackResult(AttackResult.ResultType.SUCCESS, amount);
		return result;
	}
	
	public void onHit(Entity target, InteractionHand handIn, ExtendedDamageSource damagesource, float amount) {
		int j = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, this.getValidItemInHand(handIn));
		
		if (target instanceof LivingEntity) {
			if (j > 0 && !target.isOnFire()) {
				target.setSecondsOnFire(j * 4);
			}
		}
	}
	
	public boolean onDrop(LivingDropsEvent event) {
		return false;
	}
	
	public void gatherDamageDealt(ExtendedDamageSource source, float amount) {}
	
	public void setStunReductionOnHit() {
		this.stunTimeReduction += (1.0F - this.stunTimeReduction) * 0.8F;
	}
	
	public float getStunTimeTimeReduction() {
		return this.stunTimeReduction;
	}
	
	public void knockBackEntity(Vec3 sourceLocation, float power) {
		double d1 = sourceLocation.x() - this.original.getX();
        double d0;
        
		for (d0 = sourceLocation.z() - this.original.getZ(); d1 * d1 + d0 * d0 < 1.0E-4D; d0 = (Math.random() - Math.random()) * 0.01D) {
            d1 = (Math.random() - Math.random()) * 0.01D;
        }
		
		if (this.original.getRandom().nextDouble() >= this.original.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) {
        	Vec3 vec = this.original.getDeltaMovement();
        	
        	this.original.hasImpulse = true;
            float f = (float) Math.sqrt(d1 * d1 + d0 * d0);
            
            double x = vec.x;
            double y = vec.y;
            double z = vec.z;
            
            x /= 2.0D;
            z /= 2.0D;
            x -= d1 / (double)f * (double)power;
            z -= d0 / (double)f * (double)power;

			if (!this.original.isOnGround()) {
				y /= 2.0D;
				y += (double) power;

				if (y > 0.4000000059604645D) {
					y = 0.4000000059604645D;
				}
			}
			
            this.original.setDeltaMovement(x, y, z);
            this.original.hurtMarked = true;
        }
	}
	
	public float getStunArmor() {
		AttributeInstance stunArmor = this.original.getAttribute(EpicFightAttributes.STUN_ARMOR.get());
		return (float) (stunArmor == null ? 0 : stunArmor.getValue());
	}
	
	public float getStunShield() {
		return this.original.getEntityData().get(STUN_SHIELD).floatValue();
	}
	
	public void setStunShield(float value) {
		float currentStunShield = Math.max(value, 0);
		this.original.getEntityData().set(STUN_SHIELD, currentStunShield);
	}
	
	public float getWeight() {
		return (float)this.original.getAttributeValue(EpicFightAttributes.WEIGHT.get());
	}
	
	public void rotateTo(float degree, float limit, boolean synchronizeOld) {
		LivingEntity entity = this.getOriginal();
		float amount = degree - entity.getYRot();
		
        while (amount < -180.0F) {
        	amount += 360.0F;
        }
        
        while (amount > 180.0F) {
        	amount -= 360.0F;
        }
        
        amount = Mth.clamp(amount, -limit, limit);
        float f1 = entity.getYRot() + amount;
        
		if (synchronizeOld) {
			entity.yRotO = f1;
			entity.yHeadRotO = f1;
			entity.yBodyRotO = f1;
		}
		
		entity.setYRot(f1);
		entity.yHeadRot = f1;
		entity.yBodyRot = f1;
	}
	
	public void rotateTo(Entity target, float limit, boolean partialSync) {
		double d0 = target.getX() - this.original.getX();
        double d1 = target.getZ() - this.original.getZ();
        float degree = -(float)Math.toDegrees(Mth.atan2(d0, d1));
    	this.rotateTo(degree, limit, partialSync);
	}
	
	public void playSound(SoundEvent sound, float pitchModifierMin, float pitchModifierMax) {
		this.playSound(sound, 1.0F, pitchModifierMin, pitchModifierMax);
	}
	
	public void playSound(SoundEvent sound, float volume, float pitchModifierMin, float pitchModifierMax) {
		float pitch = (this.original.getRandom().nextFloat() * 2.0F - 1.0F) * (pitchModifierMax - pitchModifierMin);
		
		if (!this.isLogicalClient()) {
			this.original.level.playSound(null, this.original.getX(), this.original.getY(), this.original.getZ(), sound, this.original.getSoundSource(), volume, 1.0F + pitch);
		} else {
			this.original.level.playLocalSound(this.original.getX(), this.original.getY(), this.original.getZ(), sound, this.original.getSoundSource(), volume, 1.0F + pitch, false);
		}
	}
	
	public LivingEntity getAttackTarget() {
		return this.original.getLastHurtMob();
	}
	
	public float getAttackDirectionPitch() {
		float partialTicks = EpicFightMod.isPhysicalClient() ? Minecraft.getInstance().getFrameTime() : 1.0F;
		float pitch = -this.getOriginal().getViewXRot(partialTicks);
		float correct = (pitch > 0) ? 0.03333F * (float)Math.pow(pitch, 2) : -0.03333F * (float)Math.pow(pitch, 2);
		return Mth.clamp(correct, -30.0F, 30.0F);
	}
	
	@OnlyIn(Dist.CLIENT)
	public OpenMatrix4f getHeadMatrix(float partialTicks) {
        float f2;
        
		if (this.state.inaction()) {
			f2 = 0;
		} else {
			float f = MathUtils.lerpBetween(this.original.yBodyRotO, this.original.yBodyRot, partialTicks);
			float f1 = MathUtils.lerpBetween(this.original.yHeadRotO, this.original.yHeadRot, partialTicks);
			f2 = f1 - f;
			
			if (this.original.getVehicle() != null) {
				if (f2 > 45.0F) {
					f2 = 45.0F;
				} else if (f2 < -45.0F) {
					f2 = -45.0F;
				}
			}
		}
		
		
		return MathUtils.getModelMatrixIntegral(0, 0, 0, 0, 0, 0, this.original.xRotO, this.original.getXRot(), f2, f2, partialTicks, 1, 1, 1);
	}
	
	@Override
	public OpenMatrix4f getModelMatrix(float partialTicks) {
		float prevYRot;
		float yRot;
		float scale = this.original.isBaby() ? 0.5F : 1.0F;
		
		if (this.original.getVehicle() instanceof LivingEntity) {
			LivingEntity ridingEntity = (LivingEntity) this.original.getVehicle();
			prevYRot = ridingEntity.yBodyRotO;
			yRot = ridingEntity.yBodyRot;
		} else {
			prevYRot = this.isLogicalClient() ? this.original.yBodyRotO : this.original.getYRot();
			yRot = this.isLogicalClient() ? this.original.yBodyRot : this.original.getYRot();
		}
		
		return MathUtils.getModelMatrixIntegral(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, prevYRot, yRot, partialTicks, scale, scale, scale);
	}
	
	public void reserveAnimation(StaticAnimation animation) {
		this.animator.reserveAnimation(animation);
		EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(new SPPlayAnimation(animation, this.original.getId(), 0.0F), this.original);
	}
	
	public void playAnimationSynchronized(StaticAnimation animation, float convertTimeModifier) {
		this.playAnimationSynchronized(animation, convertTimeModifier, SPPlayAnimation.Layer.BASE_LAYER);
	}
	
	public void playAnimationSynchronized(StaticAnimation animation, float convertTimeModifier, SPPlayAnimation.Layer playOn) {
		this.playAnimationSynchronized(animation, convertTimeModifier, SPPlayAnimation::new, playOn);
	}
	
	public void playAnimationSynchronized(StaticAnimation animation, float convertTimeModifier, AnimationPacketProvider packetProvider) {
		this.playAnimationSynchronized(animation, convertTimeModifier, packetProvider, SPPlayAnimation.Layer.BASE_LAYER);
	}
	
	public void playAnimationSynchronized(StaticAnimation animation, float convertTimeModifier, AnimationPacketProvider packetProvider, SPPlayAnimation.Layer playOn) {
		this.animator.playAnimation(animation, convertTimeModifier);
		EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(packetProvider.get(animation, convertTimeModifier, this, playOn), this.original);
	}
	
	@FunctionalInterface
	public static interface AnimationPacketProvider {
		public SPPlayAnimation get(StaticAnimation animation, float convertTimeModifier, LivingEntityPatch<?> entitypatch, SPPlayAnimation.Layer layer);
	}
	
	protected void playReboundAnimation() {
		this.getClientAnimator().playReboundAnimation();
	}
	
	public void resetSize(EntityDimensions size) {
		EntityDimensions entitysize = this.original.dimensions;
		EntityDimensions entitysize1 = size;
		this.original.dimensions = entitysize1;
	    if (entitysize1.width < entitysize.width) {
	    	double d0 = (double)entitysize1.width / 2.0D;
	    	this.original.setBoundingBox(new AABB(original.getX() - d0, original.getY(), original.getZ() - d0, original.getX() + d0,
	    			original.getY() + (double)entitysize1.height, original.getZ() + d0));
	    } else {
	    	AABB axisalignedbb = this.original.getBoundingBox();
	    	this.original.setBoundingBox(new AABB(axisalignedbb.minX, axisalignedbb.minY, axisalignedbb.minZ, axisalignedbb.minX + (double)entitysize1.width,
	    			axisalignedbb.minY + (double)entitysize1.height, axisalignedbb.minZ + (double)entitysize1.width));
	    	
	    	if (entitysize1.width > entitysize.width && !original.level.isClientSide()) {
	    		float f = entitysize.width - entitysize1.width;
	        	this.original.move(MoverType.SELF, new Vec3((double)f, 0.0D, (double)f));
	    	}
	    }
    }
	
	public void updateArmor(CapabilityItem fromCap, CapabilityItem toCap, EquipmentSlot slotType) {
		
	}
	
	public void onAttackBlocked(HurtEventPre hurtEvent, LivingEntityPatch<?> opponent) {
		
	}
	
	@SuppressWarnings("unchecked")
	public <A extends Animator> A getAnimator() {
		return (A) this.animator;
	}
	
	public ClientAnimator getClientAnimator() {
		return this.<ClientAnimator>getAnimator();
	}
	
	public ServerAnimator getServerAnimator() {
		return this.<ServerAnimator>getAnimator();
	}
	
	public abstract StaticAnimation getHitAnimation(StunType stunType);
	public void aboutToDeath() {}
	
	@Override
	public T getOriginal() {
		return original;
	}

	public SoundEvent getWeaponHitSound(InteractionHand hand) {
		return this.getAdvancedHeldItemCapability(hand).getHitSound();
	}

	public SoundEvent getSwingSound(InteractionHand hand) {
		return this.getAdvancedHeldItemCapability(hand).getSmashingSound();
	}
	
	public HitParticleType getWeaponHitParticle(InteractionHand hand) {
		return this.getAdvancedHeldItemCapability(hand).getHitParticle();
	}

	public Collider getColliderMatching(InteractionHand hand) {
		return this.getAdvancedHeldItemCapability(hand).getWeaponCollider();
	}

	public int getMaxStrikes(InteractionHand hand) {
		return (int) (hand == InteractionHand.MAIN_HAND ? this.original.getAttributeValue(EpicFightAttributes.MAX_STRIKES.get()) : 
			this.isValidOffhandItem() ? this.original.getAttributeValue(EpicFightAttributes.OFFHAND_MAX_STRIKES.get()) : this.original.getAttribute(EpicFightAttributes.MAX_STRIKES.get()).getBaseValue());
	}
	
	public float getArmorNegation(InteractionHand hand) {
		return (float) (hand == InteractionHand.MAIN_HAND ? this.original.getAttributeValue(EpicFightAttributes.ARMOR_NEGATION.get()) : 
			this.isValidOffhandItem() ? this.original.getAttributeValue(EpicFightAttributes.OFFHAND_ARMOR_NEGATION.get()) : this.original.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()).getBaseValue());
	}
	
	public float getImpact(InteractionHand hand) {
		float impact;
		int i = 0;
		
		if (hand == InteractionHand.MAIN_HAND) {
			impact = (float)this.original.getAttributeValue(EpicFightAttributes.IMPACT.get());
			i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, this.getOriginal().getMainHandItem());
		} else {
			if (this.isValidOffhandItem()) {
				impact = (float)this.original.getAttributeValue(EpicFightAttributes.OFFHAND_IMPACT.get());
				i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, this.getOriginal().getOffhandItem());
			} else {
				impact = (float)this.original.getAttribute(EpicFightAttributes.IMPACT.get()).getBaseValue();
			}
		}
		
		return impact * (1.0F + i * 0.12F);
	}
	
	public ItemStack getValidItemInHand(InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND) {
			return this.original.getItemInHand(hand);
		} else {
			return this.isValidOffhandItem() ? this.original.getItemInHand(hand) : ItemStack.EMPTY;
		}
	}
	
	public boolean isValidOffhandItem() {
		return this.getHeldItemCapability(InteractionHand.MAIN_HAND).isValidOffhandItem(this.original.getOffhandItem());
	}
	
	public boolean isTeammate(Entity entityIn) {
		if (this.original.getVehicle() != null && this.original.getVehicle().equals(entityIn)) {
			return true;
		} else if (this.isRideOrBeingRidden(entityIn)) {
			return true;
		}
		
		return this.original.isAlliedTo(entityIn) && this.original.getTeam() != null && !this.original.getTeam().isAllowFriendlyFire();
	}
	
	public Vec3 getLastAttackPosition() {
		return this.lastAttackPosition;
	}
	
	public void setLastAttackPosition() {
		this.lastAttackPosition = this.original.position();
	}
	
	private boolean isRideOrBeingRidden(Entity entityIn) {
		LivingEntity orgEntity = this.getOriginal();
		for (Entity passanger : orgEntity.getPassengers()) {
			if (passanger.equals(entityIn)) {
				return true;
			}
		}
		for (Entity passanger : entityIn.getPassengers()) {
			if (passanger.equals(orgEntity)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isFirstPerson() {
		return false;
	}
	
	public boolean shouldSkipRender() {
		return false;
	}
	
	public boolean shouldBlockMoving() {
		return false;
	}
	
	public float getYRotLimit() {
		return 12.0F;
	}
	
	public EntityState getEntityState() {
		return this.state;
	}
	
	public LivingMotion getCurrentMotion() {
		return this.currentMotion;
	}
}