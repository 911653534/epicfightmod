package yesman.epicfight.skill;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPPlayAnimation;
import yesman.epicfight.skill.SkillDataManager.SkillDataKey;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

public class KatanaPassive extends Skill {
	public static final SkillDataKey<Boolean> SHEATH = SkillDataKey.createDataKey(SkillDataManager.ValueType.BOOLEAN);
	private static final UUID EVENT_UUID = UUID.fromString("a416c93a-42cb-11eb-b378-0242ac130002");
	
	public KatanaPassive(Builder<? extends Skill> builder) {
		super(builder);
	}
	
	@Override
	public void onInitiate(SkillContainer container) {
		super.onInitiate(container);
		container.getDataManager().registerData(SHEATH);
		container.executer.getEventListener().addEventListener(EventType.ACTION_EVENT, EVENT_UUID, (event) -> {
			container.getSkill().setConsumptionSynchronize(event.getPlayerPatch(), 0.0F);
			container.getSkill().setStackSynchronize(event.getPlayerPatch(), 0);
		});
		
		container.executer.getEventListener().addEventListener(EventType.SERVER_ITEM_USE_EVENT, EVENT_UUID, (event) -> {
			this.onReset(container);
		});
	}
	
	@Override
	public void onRemoved(SkillContainer container) {
		container.executer.getEventListener().removeListener(EventType.ACTION_EVENT, EVENT_UUID);
		container.executer.getEventListener().removeListener(EventType.SERVER_ITEM_USE_EVENT, EVENT_UUID);
	}
	
	@Override
	public void onReset(SkillContainer container) {
		PlayerPatch<?> executer = container.executer;
		if (!executer.isLogicalClient()) {
			ServerPlayerPatch playerpatch = (ServerPlayerPatch) executer;
			container.getDataManager().setDataSync(SHEATH, false, playerpatch.getOriginal());
			(playerpatch).setLivingMotionCurrentItem(executer.getHeldItemCapability(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
			container.getSkill().setConsumptionSynchronize(playerpatch, 0);
		}
	}
	
	@Override
	public void setConsumption(SkillContainer container, float value) {
		PlayerPatch<?> executer = container.executer;
		if (!executer.isLogicalClient()) {
			if (this.consumption < value) {
				ServerPlayer serverPlayer = (ServerPlayer) executer.getOriginal();
				container.getDataManager().setDataSync(SHEATH, true, serverPlayer);
				((ServerPlayerPatch)container.executer).setLivingMotionCurrentItem(executer.getHeldItemCapability(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
				SPPlayAnimation msg3 = new SPPlayAnimation(Animations.BIPED_KATANA_SCRAP, serverPlayer.getId(), 0.0F, SPPlayAnimation.Layer.COMPOSITE_LAYER);
				EpicFightNetworkManager.sendToAllPlayerTrackingThisEntityWithSelf(msg3, serverPlayer);
			}
		}
		
		super.setConsumption(container, value);
	}
	
	@Override
	public boolean shouldDeactivateAutomatically(PlayerPatch<?> executer) {
		return true;
	}
	
	@Override
	public float getCooldownRegenPerSecond(PlayerPatch<?> player) {
		return player.getOriginal().isUsingItem() ? 0.0F : 1.0F;
	}
}