package yesman.epicfight.world.capabilities.item;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

public class RangedWeaponCapability extends CapabilityItem {
	protected Map<LivingMotion, StaticAnimation> rangeAnimationSet;

	public RangedWeaponCapability(Item item, StaticAnimation reload, StaticAnimation aiming, StaticAnimation shot) {
		super(item, WeaponCategory.RANGED);
		this.rangeAnimationSet = new HashMap<LivingMotion, StaticAnimation> ();
		
		if(reload != null) {
			this.rangeAnimationSet.put(LivingMotion.RELOAD, reload);
		}
		if(aiming != null) {
			this.rangeAnimationSet.put(LivingMotion.AIM, aiming);
		}
		if(shot != null) {
			this.rangeAnimationSet.put(LivingMotion.SHOT, shot);
		}
	}
	
	@Override
	public void setConfigFileAttribute(double armorNegation1, double impact1, int maxStrikes1, double armorNegation2, double impact2, int maxStrikes2) {
		this.addStyleAttributeSimple(Style.AIMING, armorNegation1, impact1, maxStrikes1);
	}
	
	@Override
	public Map<LivingMotion, StaticAnimation> getLivingMotionModifier(PlayerPatch<?> playerdata, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND) {
			return this.rangeAnimationSet;
		}
		return super.getLivingMotionModifier(playerdata, hand);
	}

	@Override
	public boolean canUseOnMount() {
		return true;
	}
	
	@Override
	public final HoldOption getHoldOption() {
		return HoldOption.TWO_HANDED;
	}
}