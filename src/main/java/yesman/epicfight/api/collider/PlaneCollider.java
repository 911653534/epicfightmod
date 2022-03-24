package yesman.epicfight.api.collider;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public class PlaneCollider extends Collider {
	private Vec3[] modelPos;
	private Vec3[] worldPos;
	
	public PlaneCollider(Vec3 center, AABB entityCallAABB) {
		super(center, entityCallAABB);
	}
	
	public PlaneCollider(AABB entityCallAABB, double centerX, double centerY, double centerZ, double pos1X, double pos1Y, double pos1Z, double pos2X, double pos2Y, double pos2Z) {
		super(new Vec3(centerX, centerY, centerZ), entityCallAABB);
		
		this.modelPos = new Vec3[2];
		this.worldPos = new Vec3[2];
		this.modelPos[0] = new Vec3(pos1X, pos1Y, pos1Z);
		this.modelPos[1] = new Vec3(pos2X, pos2Y, pos2Z);
		this.worldPos[0] = new Vec3(0.0D, 0.0D, 0.0D);
		this.worldPos[1] = new Vec3(0.0D, 0.0D, 0.0D);
	}

	@Override
	public boolean collide(Entity entity) {
		AABB opponent = entity.getBoundingBox();
		Vec3 planeNorm = this.worldPos[0].cross(this.worldPos[1]);
		Vec3 pos = new Vec3(planeNorm.x >= 0 ? opponent.maxX : opponent.minX, planeNorm.y >= 0 ? opponent.maxY : opponent.minY, planeNorm.z >= 0 ? opponent.maxZ : opponent.minZ);
		Vec3 neg = new Vec3(planeNorm.x >= 0 ? opponent.minX : opponent.maxX, planeNorm.y >= 0 ? opponent.minY : opponent.maxY, planeNorm.z >= 0 ? opponent.minZ : opponent.maxZ);
		double planeD = planeNorm.dot(this.worldCenter);
		double dot1 = planeNorm.dot(pos) - planeD;
		
		if (dot1 < 0.0D) {
			return false;
		}
		
		double dot2 = planeNorm.dot(neg) - planeD;
		
		if (dot2 > 0.0D) {
			return false;
		}

		return true;
	}

	@Override
	public void transform(OpenMatrix4f mat) {
		for (int i = 0; i < 2; i ++) {
			this.worldPos[i] = OpenMatrix4f.transform(mat.removeTranslation(), this.modelPos[i]);
		}
		
		super.transform(mat);
	}
	
	@Override
	public void drawInternal(PoseStack matrixStackIn, MultiBufferSource buffer, OpenMatrix4f pose, boolean red) {
		
	}
	
	@Override
	public void draw(PoseStack matrixStackIn, MultiBufferSource buffer, LivingEntityPatch<?> entitypatch, AttackAnimation animation, float prevElapsedTime, float elapsedTime, float partialTicks, float attackSpeed) {
		
	}
}