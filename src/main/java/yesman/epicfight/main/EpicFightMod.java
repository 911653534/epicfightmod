package yesman.epicfight.main;

import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fmlclient.ConfigGuiHandler;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.ServerAnimator;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.api.client.model.ClientModels;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.events.ClientEvents;
import yesman.epicfight.client.events.ClientModBusEvent;
import yesman.epicfight.client.events.engine.ControllEngine;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.gui.screen.IngameConfigurationScreen;
import yesman.epicfight.client.input.EpicFightKeyMappings;
import yesman.epicfight.config.ConfigManager;
import yesman.epicfight.config.ConfigurationIngame;
import yesman.epicfight.data.loot.LootModifiers;
import yesman.epicfight.events.CapabilityEvent;
import yesman.epicfight.events.EntityEvents;
import yesman.epicfight.events.ModBusEvents;
import yesman.epicfight.events.PlayerEvents;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.Models;
import yesman.epicfight.gameasset.Skills;
import yesman.epicfight.network.EpicFightDataSerializers;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.world.EpicFightGamerules;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.ItemCapabilityListener;
import yesman.epicfight.world.capabilities.provider.ProviderEntity;
import yesman.epicfight.world.capabilities.provider.ProviderItem;
import yesman.epicfight.world.capabilities.provider.ProviderProjectile;
import yesman.epicfight.world.effect.EpicFightMobEffects;
import yesman.epicfight.world.effect.EpicFightPotions;
import yesman.epicfight.world.entity.EpicFightEntities;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.item.EpicFightItems;

@Mod("epicfight")
public class EpicFightMod {
	public static final String MODID = "epicfight";
	public static final String CONFIG_FILE_PATH = EpicFightMod.MODID + ".toml";
	public static final Logger LOGGER = LogManager.getLogger(MODID);
	public static ConfigurationIngame CLIENT_INGAME_CONFIG;
	private static EpicFightMod instance;
	
	public static EpicFightMod getInstance() {
		return instance;
	}
	
	public final AnimationManager animationManager;
	private Function<LivingEntityPatch<?>, Animator> animatorProvider;
	private Models<?> model;
	
    public EpicFightMod() {
    	this.animationManager = new AnimationManager();
    	instance = this;
    	
    	ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigManager.CLIENT_CONFIG);
    	
    	IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
    	bus.addListener(this::doClientStuff);
    	bus.addListener(this::doCommonStuff);
    	bus.addListener(this::doServerStuff);
    	bus.addListener(EpicFightAttributes::registerNewMobs);
    	bus.addListener(EpicFightAttributes::modifyExistingMobs);
    	bus.addListener(EpicFightCapabilities::registerCapabilities);
    	bus.addListener(Animations::registerAnimations);
    	
    	this.animationManager.registerAnimations();
    	Skills.init();
    	
    	EpicFightMobEffects.EFFECTS.register(bus);
    	EpicFightPotions.POTIONS.register(bus);
        EpicFightAttributes.ATTRIBUTES.register(bus);
        EpicFightItems.ITEMS.register(bus);
        EpicFightParticles.PARTICLES.register(bus);
        EpicFightEntities.ENTITIES.register(bus);
        LootModifiers.SERIALIZERS.register(bus);
        EpicFightDataSerializers.DATA_SERIALIZERS.register(bus);
        
        MinecraftForge.EVENT_BUS.addListener(this::reloadListnerEvent);
        MinecraftForge.EVENT_BUS.register(EntityEvents.class);
        MinecraftForge.EVENT_BUS.register(ModBusEvents.class);
        MinecraftForge.EVENT_BUS.register(CapabilityEvent.class);
        MinecraftForge.EVENT_BUS.register(PlayerEvents.class);
        
        ConfigManager.loadConfig(ConfigManager.CLIENT_CONFIG, FMLPaths.CONFIGDIR.get().resolve(MODID + "-client.toml").toString());
        ConfigManager.loadConfig(ConfigManager.COMMON_CONFIG, FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_PATH).toString());
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class, () -> new ConfigGuiHandler.ConfigGuiFactory(IngameConfigurationScreen::new));
    }
    
	private void doClientStuff(final FMLClientSetupEvent event) {
    	new ClientEngine();
		ProviderEntity.makeMapClient();
		
		ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
		ClientModels.LOGICAL_CLIENT.loadMeshData(resourceManager);
		ClientModels.LOGICAL_CLIENT.loadArmatureData(resourceManager);
		Models.LOGICAL_SERVER.loadArmatureData(resourceManager);
		this.animationManager.loadAnimationsInit(resourceManager);
		Animations.buildClient();
		
		ClientEngine.instance.renderEngine.buildRenderer();
		EpicFightKeyMappings.registerKeys();
		MinecraftForge.EVENT_BUS.register(ControllEngine.Events.class);
        MinecraftForge.EVENT_BUS.register(RenderEngine.Events.class);
        MinecraftForge.EVENT_BUS.register(ClientModBusEvent.class);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        ((ReloadableResourceManager)resourceManager).registerReloadListener(ClientModels.LOGICAL_CLIENT);
        ((ReloadableResourceManager)resourceManager).registerReloadListener(this.animationManager);
        CLIENT_INGAME_CONFIG = new ConfigurationIngame();
        this.animatorProvider = ClientAnimator::getAnimator;
        this.model = ClientModels.LOGICAL_CLIENT;
    }
	
	private void doServerStuff(final FMLDedicatedServerSetupEvent event) {
		Models.LOGICAL_SERVER.loadArmatureData(null);
		this.animationManager.loadAnimationsInit(null);
		this.animatorProvider = ServerAnimator::getAnimator;
		this.model = Models.LOGICAL_SERVER;
	}
	
	private void doCommonStuff(final FMLCommonSetupEvent event) {
		event.enqueueWork(EpicFightPotions::addRecipes);
		event.enqueueWork(EpicFightNetworkManager::registerPackets);
		event.enqueueWork(ProviderItem::registerWeaponTypesByClass);
		event.enqueueWork(ProviderEntity::registerPatches);
		event.enqueueWork(ProviderProjectile::registerPatches);
		event.enqueueWork(EpicFightGamerules::registerRules);
		event.enqueueWork(EpicFightEntities::registerSpawnPlacements);
    }
	
	private void reloadListnerEvent(final AddReloadListenerEvent event) {
		event.addListener(new ItemCapabilityListener());
	}
	
	public static Animator getAnimator(LivingEntityPatch<?> entitypatch) {
		return EpicFightMod.getInstance().animatorProvider.apply(entitypatch);
	}
	
	public static Models<?> getModelContainer(boolean isLogicalClient) {
		return EpicFightMod.getInstance().model.getModelContainer(isLogicalClient);
	}
	
	public static boolean isPhysicalClient() {
    	return FMLEnvironment.dist == Dist.CLIENT;
    }
}