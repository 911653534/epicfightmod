package yesman.epicfight.world.capabilities.entitypatch.player;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.utils.game.AttackResult;
import yesman.epicfight.api.utils.game.ExtendedDamageSource;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPAddSkill;
import yesman.epicfight.network.server.SPChangeLivingMotion;
import yesman.epicfight.network.server.SPChangePlayerYaw;
import yesman.epicfight.network.server.SPChangeSkill;
import yesman.epicfight.network.server.SPPlayAnimation;
import yesman.epicfight.skill.SkillCategory;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.skill.CapabilitySkill;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.entity.eventlistener.HurtEventPre;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

public class ServerPlayerPatch extends PlayerPatch<ServerPlayer> {
	private LivingEntity attackTarget;
	private Map<LivingMotion, StaticAnimation> mainhandCompositeLivingMotions = Maps.<LivingMotion, StaticAnimation>newHashMap();
	private Map<LivingMotion, StaticAnimation> offhandCompositeLivingMotions = Maps.<LivingMotion, StaticAnimation>newHashMap();
	
	@Override
	public void onJoinWorld(ServerPlayer entityIn, EntityJoinWorldEvent event) {
		super.onJoinWorld(entityIn, event);
		CapabilitySkill skillCapability = this.getSkillCapability();
		
		for (SkillContainer skill : skillCapability.skillContainers) {
			if (skill.getSkill() != null && skill.getSkill().getCategory().shouldSyncronized()) {
				EpicFightNetworkManager.sendToPlayer(new SPChangeSkill(skill.getSkill().getCategory().getIndex(), skill.getSkill().getName(),
						SPChangeSkill.State.ENABLE), this.original);
			}
		}
		
		List<String> learnedSkill = Lists.newArrayList();
		
		for (SkillCategory category : SkillCategory.values()) {
			if (skillCapability.hasCategory(category)) {
				learnedSkill.addAll(Lists.newArrayList(skillCapability.getLearnedSkills(category).stream().map((skill)->skill.getName()).iterator()));
			}
		}
		
		EpicFightNetworkManager.sendToPlayer(new SPAddSkill(learnedSkill.toArray(new String[0])), this.original);
	}
	
	@Override
	public void gatherDamageDealt(ExtendedDamageSource source, float amount) {
		if (source.isBasicAttack()) {
			SkillContainer container = this.getSkill(SkillCategory.WEAPON_SPECIAL_ATTACK);
			
			if (!container.isFull() && container.hasSkill(this.getHeldItemCapability(InteractionHand.MAIN_HAND).getSpecialAttack(this))) {
				float value = container.getResource() + amount;
				
				if (value > 0.0F) {
					this.getSkill(SkillCategory.WEAPON_SPECIAL_ATTACK).getSkill().setConsumptionSynchronize(this, value);
				}
			}
		}
	}
	
	@Override
	public void updateMotion(boolean considerInaction) {
		;
	}
	
	public void updateHeldItem(CapabilityItem fromCap, CapabilityItem toCap, ItemStack from, ItemStack to, InteractionHand hand) {
		CapabilityItem mainHandCap = (hand == InteractionHand.MAIN_HAND) ? toCap : this.getHeldItemCapability(InteractionHand.MAIN_HAND);
		mainHandCap.changeWeaponSpecialSkill(this);
		
		if (hand == InteractionHand.OFF_HAND) {
			if (!from.isEmpty()) {
				from.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_ATTACK_DAMAGE.get())::removeModifier);
				from.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_ATTACK_SPEED.get())::removeModifier);
			}
			if (!fromCap.isEmpty()) {
				fromCap.getAttributeModifiers(EquipmentSlot.MAINHAND, this).get(EpicFightAttributes.ARMOR_NEGATION.get()).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_ARMOR_NEGATION.get())::removeModifier);
				fromCap.getAttributeModifiers(EquipmentSlot.MAINHAND, this).get(EpicFightAttributes.IMPACT.get()).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_IMPACT.get())::removeModifier);
				fromCap.getAttributeModifiers(EquipmentSlot.MAINHAND, this).get(EpicFightAttributes.MAX_STRIKES.get()).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_MAX_STRIKES.get())::removeModifier);
			}
			
			if (!to.isEmpty()) {
				to.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_ATTACK_DAMAGE.get())::addTransientModifier);
				to.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_ATTACK_SPEED.get())::addTransientModifier);
			}
			if (!toCap.isEmpty()) {
				toCap.getAttributeModifiers(EquipmentSlot.MAINHAND, this).get(EpicFightAttributes.ARMOR_NEGATION.get()).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_ARMOR_NEGATION.get())::addTransientModifier);
				toCap.getAttributeModifiers(EquipmentSlot.MAINHAND, this).get(EpicFightAttributes.IMPACT.get()).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_IMPACT.get())::addTransientModifier);
				toCap.getAttributeModifiers(EquipmentSlot.MAINHAND, this).get(EpicFightAttributes.MAX_STRIKES.get()).forEach(this.original.getAttribute(EpicFightAttributes.OFFHAND_MAX_STRIKES.get())::addTransientModifier);
			}
		}
		
		this.setLivingMotionCurrentItem(toCap, hand);
	}
	
	@Override
	public void updateArmor(CapabilityItem fromCap, CapabilityItem toCap, EquipmentSlot slotType) {
		
	}
	
	public void setLivingMotionCurrentItem(CapabilityItem capabilityItem, InteractionHand hand) {
		this.resetCompositeLivingMotions(hand);
		Map<LivingMotion, StaticAnimation> motionChanger = capabilityItem.getLivingMotionModifier(this, hand);
		List<LivingMotion> motions = Lists.<LivingMotion>newArrayList();
		List<StaticAnimation> animations = Lists.<StaticAnimation>newArrayList();
		
		for (Map.Entry<LivingMotion, StaticAnimation> entry : motionChanger.entrySet()) {
			this.addCompositeLivingMotion(entry.getKey(), entry.getValue(), hand);
		}
		
		for (Map.Entry<LivingMotion, StaticAnimation> finalEntry : this.getCompositeLivingMotions()) {
			motions.add(finalEntry.getKey());
			animations.add(finalEntry.getValue());
		}
		
		LivingMotion[] motionarr = motions.toArray(new LivingMotion[0]);
		StaticAnimation[] animationarr = animations.toArray(new StaticAnimation[0]);
		SPChangeLivingMotion msg = new SPChangeLivingMotion(this.original.getId(), motions.size(), SPPlayAnimation.Layer.COMPOSITE_LAYER);
		msg.setMotions(motionarr);
		msg.setAnimations(animationarr);
		EpicFightNetworkManager.sendToAllPlayerTrackingThisEntityWithSelf(msg, this.original);
	}
	
	private void addCompositeLivingMotion(LivingMotion motion, StaticAnimation animation, InteractionHand hand) {
		Map<LivingMotion, StaticAnimation> CompositeMotion = hand == InteractionHand.MAIN_HAND ? this.mainhandCompositeLivingMotions : this.offhandCompositeLivingMotions;
		
		if (animation != null) {
			CompositeMotion.put(motion, animation);
		}
	}
	
	private void resetCompositeLivingMotions(InteractionHand hand) {
		Map<LivingMotion, StaticAnimation> compositeMotion = hand == InteractionHand.MAIN_HAND ? this.mainhandCompositeLivingMotions : this.offhandCompositeLivingMotions;
		compositeMotion.clear();
	}
	
	public Set<Map.Entry<LivingMotion, StaticAnimation>> getCompositeLivingMotions() {
		Map<LivingMotion, StaticAnimation> map = Maps.newHashMap();
		map.putAll(this.mainhandCompositeLivingMotions);
		
		for (Map.Entry<LivingMotion, StaticAnimation> entry : this.offhandCompositeLivingMotions.entrySet()) {
			map.computeIfAbsent(entry.getKey(), (key) -> entry.getValue());
		}
		
		return map.entrySet();
	}
	
	@Override
	public void playAnimationSynchronized(StaticAnimation animation, float convertTimeModifier, AnimationPacketProvider packetProvider, SPPlayAnimation.Layer playOn) {
		super.playAnimationSynchronized(animation, convertTimeModifier, packetProvider, playOn);
		EpicFightNetworkManager.sendToPlayer(packetProvider.get(animation, convertTimeModifier, this, playOn), this.original);
	}
	
	@Override
	public void reserveAnimation(StaticAnimation animation) {
		super.reserveAnimation(animation);
		EpicFightNetworkManager.sendToPlayer(new SPPlayAnimation(animation, this.original.getId(), 0.0F), this.original);
	}
	
	@Override
	public void changeYaw(float amount) {
		super.changeYaw(amount);
		EpicFightNetworkManager.sendToAllPlayerTrackingThisEntityWithSelf(new SPChangePlayerYaw(this.original.getId(), this.yaw), this.original);
	}
	
	@Override
	public AttackResult tryHurt(DamageSource damageSource, float amount) {
		HurtEventPre hitEvent = new HurtEventPre(this, damageSource, amount);
		
		if (this.getEventListener().triggerEvents(EventType.HURT_EVENT_PRE, hitEvent)) {
			return new AttackResult(hitEvent.getResult(), hitEvent.getAmount());
		} else {
			return super.tryHurt(damageSource, amount);
		}
	}
	
	@Override
	public ServerPlayer getOriginal() {
		return this.original;
	}
	
	public void setAttackTarget(LivingEntity entity) {
		this.attackTarget = entity;
	}
	
	@Override
	public LivingEntity getAttackTarget() {
		return this.attackTarget;
	}
}