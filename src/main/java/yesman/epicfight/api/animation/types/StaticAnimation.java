
package yesman.epicfight.api.animation.types;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.google.common.collect.Maps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.property.Property;
import yesman.epicfight.api.animation.property.Property.StaticAnimationProperty;
import yesman.epicfight.api.client.animation.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.client.animation.JointMask;
import yesman.epicfight.api.client.animation.JointMask.BindModifier;
import yesman.epicfight.api.model.JsonModelLoader;
import yesman.epicfight.api.model.Model;
import yesman.epicfight.config.ConfigurationIngame;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public class StaticAnimation extends DynamicAnimation {
	protected final Map<Property<?>, Object> properties = Maps.<Property<?>, Object>newHashMap();
	protected final Model model;
	protected final ResourceLocation resourceLocation;
	protected final int namespaceId;
	protected final int animationId;
	
	public StaticAnimation() {
		super(0.0F, false);
		this.namespaceId = -1;
		this.animationId = -1;
		this.resourceLocation = null;
		this.model = null;
	}
	
	public StaticAnimation(boolean repeatPlay, String path, Model model) {
		this(ConfigurationIngame.GENERAL_ANIMATION_CONVERT_TIME, repeatPlay, path, model);
	}
	
	public StaticAnimation(float convertTime, boolean isRepeat, String path, Model model) {
		super(convertTime, isRepeat);
		AnimationManager animationManager = EpicFightMod.getInstance().animationManager;
		this.namespaceId = animationManager.getNamespaceHash();
		this.animationId = animationManager.getIdCounter();
		animationManager.getIdMap().put(this.animationId, this);
		this.resourceLocation = new ResourceLocation(animationManager.getModid(), "animmodels/animations/" + path);
		animationManager.getNameMap().put(this.resourceLocation, this);
		this.model = model;
	}
	
	public StaticAnimation(float convertTime, boolean repeatPlay, String path, Model model, boolean notRegisteredInAnimationManager) {
		super(convertTime, repeatPlay);
		this.namespaceId = -1;
		this.animationId = -1;
		this.resourceLocation = new ResourceLocation(EpicFightMod.getInstance().animationManager.getModid(), "animmodels/animations/" + path);
		this.model = model;
	}
	
	public static void load(ResourceManager resourceManager, StaticAnimation animation) {
		ResourceLocation extenderPath = new ResourceLocation(animation.resourceLocation.getNamespace(), animation.resourceLocation.getPath() + ".json");
		(new JsonModelLoader(resourceManager, extenderPath)).loadStaticAnimation(animation);
	}
	
	public static void loadBothSide(ResourceManager resourceManager, StaticAnimation animation) {
		ResourceLocation extenderPath = new ResourceLocation(animation.resourceLocation.getNamespace(), animation.resourceLocation.getPath() + ".json");
		(new JsonModelLoader(resourceManager, extenderPath)).loadStaticAnimationBothSide(animation);
	}
	
	public void loadAnimation(ResourceManager resourceManager) {
		try {
			int id = Integer.parseInt(this.resourceLocation.getPath().substring(22));
			StaticAnimation animation = EpicFightMod.getInstance().animationManager.findAnimation(this.namespaceId, id);
			this.jointTransforms = animation.jointTransforms;
			this.setTotalTime(animation.totalTime);
		} catch (NumberFormatException e) {
			load(resourceManager, this);
		}
	}
	
	@Override
	public void tick(LivingEntityPatch<?> entitypatch) {
		this.getProperty(StaticAnimationProperty.EVENTS).ifPresent((events) -> {
			AnimationPlayer player = entitypatch.getAnimator().getPlayerFor(this);
			
			if (player != null) {
				float prevElapsed = player.getPrevElapsedTime();
				float elapsed = player.getElapsedTime();
				
				for (Event event : events) {
					if (event.time < prevElapsed || event.time >= elapsed) {
						continue;
					} else {
						event.testAndExecute(entitypatch);
					}
				}
			}
		});
	}
	
	@Override
	public boolean isJointEnabled(LivingEntityPatch<?> entitypatch, String joint) {
		if (!super.isJointEnabled(entitypatch, joint)) {
			return false;
		} else {
			boolean bool = this.getProperty(ClientAnimationProperties.POSE_MODIFIER).map((bindModifier) -> {
				return !bindModifier.isMasked(entitypatch.getCurrentMotion(), joint);
			}).orElse(true);
			
			return bool;
		}
	}
	
	@Override
	public BindModifier getBindModifier(LivingEntityPatch<?> entitypatch, String joint) {
		return this.getProperty(ClientAnimationProperties.POSE_MODIFIER).map((jointMaskEntry) -> {
			List<JointMask> list = jointMaskEntry.getMask(entitypatch.getCurrentMotion());
			int position = list.indexOf(JointMask.of(joint));
			
			if (position >= 0) {
				return list.get(position).getBindModifier();
			} else {
				return null;
			}
		}).orElse(null);
	}
	
	@Override
	public int getNamespaceId() {
		return this.namespaceId;
	}
	
	@Override
	public int getId() {
		return this.animationId;
	}
	
	public ResourceLocation getLocation() {
		return this.resourceLocation;
	}
	
	public Model getModel() {
		return this.model;
	}
	
	public boolean isBasicAttackAnimation() {
		return false;
	}
	
	@Override
	public String toString() {
		String classPath = this.getClass().toString();
		return classPath.substring(classPath.lastIndexOf(".") + 1) + " " + this.getLocation();
	}
	
	public <V> StaticAnimation addProperty(StaticAnimationProperty<V> propertyType, V value) {
		this.properties.put(propertyType, value);
		return this;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <V> Optional<V> getProperty(Property<V> propertyType) {
		return (Optional<V>) Optional.ofNullable(this.properties.get(propertyType));
	}
	
	@OnlyIn(Dist.CLIENT)
	public Layer.Priority getPriority() {
		return this.getProperty(ClientAnimationProperties.PRIORITY).orElse(Layer.Priority.LOWEST);
	}
	
	public static class Event implements Comparable<Event> {
		final float time;
		final Side executionSide;
		final Consumer<LivingEntityPatch<?>> event;
		
		private Event(float time, Side executionSide, Consumer<LivingEntityPatch<?>> event) {
			this.time = time;
			this.executionSide = executionSide;
			this.event = event;
		}
		
		@Override
		public int compareTo(Event arg0) {
			if(this.time == arg0.time) {
				return 0;
			} else {
				return this.time > arg0.time ? 1 : -1;
			}
		}
		
		public void testAndExecute(LivingEntityPatch<?> entitypatch) {
			if (this.executionSide.predicate.test(entitypatch.isLogicalClient())) {
				this.event.accept(entitypatch);
			}
		}
		
		public static Event create(float time, Consumer<LivingEntityPatch<?>> event, Side isRemote) {
			return new Event(time, isRemote, event);
		}
		
		public enum Side {
			CLIENT((isLogicalClient) -> isLogicalClient), SERVER((isLogicalClient) -> !isLogicalClient), BOTH((isLogicalClient) -> true);
			
			Predicate<Boolean> predicate;
			
			Side(Predicate<Boolean> predicate) {
				this.predicate = predicate;
			}
		}
	}
}