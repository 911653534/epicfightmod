package yesman.epicfight.world.capabilities.entitypatch;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.api.utils.game.ExtendedDamageSource.StunType;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.MobCombatBehaviors;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPChangeLivingMotion;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.CapabilityItem.Style;
import yesman.epicfight.world.capabilities.item.CapabilityItem.WeaponCategory;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

public abstract class HumanoidMobPatch<T extends PathfinderMob> extends MobPatch<T> {
	protected Map<WeaponCategory, Map<CapabilityItem.Style, Set<Pair<LivingMotion, StaticAnimation>>>> weaponLivingMotions;
	protected Map<WeaponCategory, Map<CapabilityItem.Style, CombatBehaviors.Builder<HumanoidMobPatch<?>>>> weaponAttackMotions;
	
	public HumanoidMobPatch(Faction faction) {
		super(faction);
		this.setWeaponMotions();
	}
	
	@Override
	protected void initAI() {
		super.initAI();
		
		if (this.original.getVehicle() != null && this.original.getVehicle() instanceof Mob) {
			this.setAIAsMounted(this.original.getVehicle());
		} else {
			this.setAIAsInfantry(this.original.getMainHandItem().getItem() instanceof ProjectileWeaponItem);
		}
	}
	
	@Override
	public void onStartTracking(ServerPlayer trackingPlayer) {
		this.modifyLivingMotionByCurrentItem();
	}
	
	protected void setWeaponMotions() {
		this.weaponLivingMotions = Maps.newHashMap();
		this.weaponLivingMotions.put(WeaponCategory.GREATSWORD, ImmutableMap.of(
			CapabilityItem.Style.TWO_HAND, Set.of(
				Pair.of(LivingMotion.WALK, Animations.BIPED_WALK_TWOHAND),
				Pair.of(LivingMotion.CHASE, Animations.BIPED_WALK_TWOHAND)
			)
		));
		
		this.weaponAttackMotions = Maps.newHashMap();
		this.weaponAttackMotions.put(WeaponCategory.AXE, ImmutableMap.of(CapabilityItem.Style.COMMON, MobCombatBehaviors.HUMANOID_ONEHAND_TOOLS));
		this.weaponAttackMotions.put(WeaponCategory.HOE, ImmutableMap.of(CapabilityItem.Style.COMMON, MobCombatBehaviors.HUMANOID_ONEHAND_TOOLS));
		this.weaponAttackMotions.put(WeaponCategory.PICKAXE, ImmutableMap.of(CapabilityItem.Style.COMMON, MobCombatBehaviors.HUMANOID_ONEHAND_TOOLS));
		this.weaponAttackMotions.put(WeaponCategory.SHOVEL, ImmutableMap.of(CapabilityItem.Style.COMMON, MobCombatBehaviors.HUMANOID_ONEHAND_TOOLS));
		this.weaponAttackMotions.put(WeaponCategory.SWORD, ImmutableMap.of(CapabilityItem.Style.ONE_HAND, MobCombatBehaviors.HUMANOID_ONEHAND_TOOLS, CapabilityItem.Style.TWO_HAND, MobCombatBehaviors.HUMANOID_DUAL_SWORD));
		this.weaponAttackMotions.put(WeaponCategory.GREATSWORD, ImmutableMap.of(CapabilityItem.Style.TWO_HAND, MobCombatBehaviors.HUMANOID_GREATSWORD));
		this.weaponAttackMotions.put(WeaponCategory.KATANA, ImmutableMap.of(CapabilityItem.Style.TWO_HAND, MobCombatBehaviors.HUMANOID_KATANA));
		this.weaponAttackMotions.put(WeaponCategory.LONGSWORD, ImmutableMap.of(CapabilityItem.Style.TWO_HAND, MobCombatBehaviors.HUMANOID_LONGSWORD));
		this.weaponAttackMotions.put(WeaponCategory.TACHI, ImmutableMap.of(CapabilityItem.Style.TWO_HAND, MobCombatBehaviors.HUMANOID_TACHI));
		this.weaponAttackMotions.put(WeaponCategory.SPEAR, ImmutableMap.of(CapabilityItem.Style.ONE_HAND, MobCombatBehaviors.HUMANOID_SPEAR_ONEHAND, CapabilityItem.Style.TWO_HAND, MobCombatBehaviors.HUMANOID_SPEAR_TWOHAND));
		this.weaponAttackMotions.put(WeaponCategory.FIST, ImmutableMap.of(CapabilityItem.Style.COMMON, MobCombatBehaviors.HUMANOID_FIST));
		this.weaponAttackMotions.put(WeaponCategory.DAGGER, ImmutableMap.of(CapabilityItem.Style.ONE_HAND, MobCombatBehaviors.HUMANOID_ONEHAND_DAGGER, CapabilityItem.Style.TWO_HAND, MobCombatBehaviors.HUMANOID_TWOHAND_DAGGER));
	}
	
	protected CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
		CapabilityItem itemCap = this.getHoldingItemCapability(InteractionHand.MAIN_HAND);
		
		if (this.weaponAttackMotions.containsKey(itemCap.getWeaponCategory())) {
			Map<CapabilityItem.Style, CombatBehaviors.Builder<HumanoidMobPatch<?>>> motionByStyle = this.weaponAttackMotions.get(itemCap.getWeaponCategory());
			CapabilityItem.Style style = itemCap.getStyle(this);
			
			if (motionByStyle.containsKey(style) || motionByStyle.containsKey(CapabilityItem.Style.COMMON)) {
				return motionByStyle.getOrDefault(style, motionByStyle.get(CapabilityItem.Style.COMMON));
			}
		}
		
		return null;
	}
	
	public void setAIAsInfantry(boolean holdingRanedWeapon) {
		CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = this.getHoldingItemWeaponMotionBuilder();
		
		if (builder != null) {
			this.original.goalSelector.addGoal(0, new AnimatedAttackGoal<>(this, builder.build(this), this.getOriginal(), 1.0D, true));
		}
	}
	
	public void setAIAsMounted(Entity ridingEntity) {
		if (this.isArmed()) {
			if (ridingEntity instanceof AbstractHorse) {
				this.original.goalSelector.addGoal(0, new AnimatedAttackGoal<>(this, MobCombatBehaviors.MOUNT_HUMANOID_BEHAVIORS.build(this), this.original, 1.0D, false));
			}
		}
	}
	
	protected final void commonMobAnimatorInit(ClientAnimator clientAnimator) {
		clientAnimator.addLivingAnimation(LivingMotion.IDLE, Animations.BIPED_IDLE);
		clientAnimator.addLivingAnimation(LivingMotion.WALK, Animations.BIPED_WALK);
		clientAnimator.addLivingAnimation(LivingMotion.FALL, Animations.BIPED_FALL);
		clientAnimator.addLivingAnimation(LivingMotion.MOUNT, Animations.BIPED_MOUNT);
		clientAnimator.addLivingAnimation(LivingMotion.DEATH, Animations.BIPED_DEATH);
		clientAnimator.setCurrentMotionsAsDefault();
	}
	
	protected final void commonAggresiveMobAnimatorInit(ClientAnimator clientAnimator) {
		clientAnimator.addLivingAnimation(LivingMotion.IDLE, Animations.BIPED_IDLE);
		clientAnimator.addLivingAnimation(LivingMotion.WALK, Animations.BIPED_WALK);
		clientAnimator.addLivingAnimation(LivingMotion.CHASE, Animations.BIPED_WALK);
		clientAnimator.addLivingAnimation(LivingMotion.FALL, Animations.BIPED_FALL);
		clientAnimator.addLivingAnimation(LivingMotion.MOUNT, Animations.BIPED_MOUNT);
		clientAnimator.addLivingAnimation(LivingMotion.DEATH, Animations.BIPED_DEATH);
		clientAnimator.setCurrentMotionsAsDefault();
	}
	
	@Override
	public void updateHeldItem(CapabilityItem fromCap, CapabilityItem toCap, ItemStack from, ItemStack to, InteractionHand hand) {
		this.initAI();
		
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
		
		this.modifyLivingMotionByCurrentItem();
	}
	
	public void modifyLivingMotionByCurrentItem() {
		this.getAnimator().resetMotions();
		
		CapabilityItem mainhandCap = this.getHoldingItemCapability(InteractionHand.MAIN_HAND);
		CapabilityItem offhandCap = this.getAdvancedHoldingItemCapability(InteractionHand.OFF_HAND);
		Map<LivingMotion, StaticAnimation> motionModifier = Maps.newHashMap();
		
		offhandCap.getLivingMotionModifier(this, InteractionHand.OFF_HAND).forEach(motionModifier::put);
		mainhandCap.getLivingMotionModifier(this, InteractionHand.MAIN_HAND).forEach(motionModifier::put);
		
		for (Map.Entry<LivingMotion, StaticAnimation> entry : motionModifier.entrySet()) {
			this.getAnimator().addLivingAnimation(entry.getKey(), entry.getValue());
		}
		
		if (this.weaponLivingMotions != null && this.weaponLivingMotions.containsKey(mainhandCap.getWeaponCategory())) {
			Map<CapabilityItem.Style, Set<Pair<LivingMotion, StaticAnimation>>> mapByStyle = this.weaponLivingMotions.get(mainhandCap.getWeaponCategory());
			Style style = mainhandCap.getStyle(this);
			
			if (mapByStyle.containsKey(style) || mapByStyle.containsKey(CapabilityItem.Style.COMMON)) {
				Set<Pair<LivingMotion, StaticAnimation>> animModifierSet = mapByStyle.getOrDefault(style, mapByStyle.get(CapabilityItem.Style.COMMON));
				
				for (Pair<LivingMotion, StaticAnimation> pair : animModifierSet) {
					this.animator.addLivingAnimation(pair.getFirst(), pair.getSecond());
				}
			}
		}
		
		SPChangeLivingMotion msg = new SPChangeLivingMotion(this.original.getId());
		msg.putEntries(this.getAnimator().getLivingAnimationEntrySet());
		EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(msg, this.original);
	}
	
	public boolean isArmed() {
		Item heldItem = this.original.getMainHandItem().getItem();
		return heldItem instanceof SwordItem || heldItem instanceof DiggerItem || heldItem instanceof TridentItem;
	}
	
	@Override
	public void onMount(boolean isMountOrDismount, Entity ridingEntity) {
		if (this.original == null) {
			return;
		}
		
		this.resetCombatAI();
		
		if (isMountOrDismount) {
			this.setAIAsMounted(ridingEntity);
		} else {
			this.setAIAsInfantry(this.original.getMainHandItem().getItem() instanceof ProjectileWeaponItem);
		}
	}
	
	@Override
	public StaticAnimation getHitAnimation(StunType stunType) {
		if (this.original.getVehicle() != null) {
			return Animations.BIPED_HIT_ON_MOUNT;
		} else {
			switch (stunType) {
			case LONG:
				return Animations.BIPED_HIT_LONG;
			case SHORT:
				return Animations.BIPED_HIT_SHORT;
			case HOLD:
				return Animations.BIPED_HIT_SHORT;
			case KNOCKDOWN:
				return Animations.BIPED_KNOCKDOWN;
			case FALL:
				return Animations.BIPED_LANDING;
			case NONE:
				return null;
			}
		}
		
		return null;
	}
}