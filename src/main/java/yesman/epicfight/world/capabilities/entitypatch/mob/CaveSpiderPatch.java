package yesman.epicfight.world.capabilities.entitypatch.mob;

import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CaveSpider;
import yesman.epicfight.api.utils.game.ExtendedDamageSource;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.gameasset.MobCombatBehaviors;
import yesman.epicfight.world.entity.ai.goal.AttackPatternGoal;
import yesman.epicfight.world.entity.ai.goal.ChasingGoal;

public class CaveSpiderPatch extends SpiderPatch<CaveSpider> {
	@Override
	protected void initAI() {
		super.initAI();
		this.original.goalSelector.addGoal(0, new AttackPatternGoal(this, this.original, 0.0D, 2.0D, true, MobCombatBehaviors.SPIDER));
        this.original.goalSelector.addGoal(1, new ChasingGoal(this, this.original, 1.0D, false));
	}
	
	@Override
	public void onHit(Entity target, InteractionHand handIn, ExtendedDamageSource source, float amount) {
		if (target instanceof LivingEntity) {
			int i = 0;
			
            if (this.original.level.getDifficulty() == Difficulty.NORMAL) {
                i = 7;
            } else if (this.original.level.getDifficulty() == Difficulty.HARD) {
                i = 15;
            }
            
            if (i > 0) {
                ((LivingEntity)target).addEffect(new MobEffectInstance(MobEffects.POISON, i * 20, 0));
            }
		}
	}
	
	@Override
	public OpenMatrix4f getModelMatrix(float partialTicks) {
		OpenMatrix4f mat = super.getModelMatrix(partialTicks);
		return OpenMatrix4f.scale(new Vec3f(0.7F, 0.7F, 0.7F), mat, mat);
	}
}