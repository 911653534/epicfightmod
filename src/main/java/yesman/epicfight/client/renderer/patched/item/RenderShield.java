package yesman.epicfight.client.renderer.patched.item;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;

@OnlyIn(Dist.CLIENT)
public class RenderShield extends RenderItemMirror {
	public RenderShield() {
		super();
		this.leftHandCorrectionMatrix = new OpenMatrix4f().translate(0F, 0.5F, -0.13F).rotateDeg(180F, Vec3f.Y_AXIS).rotateDeg(90F, Vec3f.X_AXIS).translate(Vec3f.scale(Vec3f.Y_AXIS, null, 0.1F));
	}
}