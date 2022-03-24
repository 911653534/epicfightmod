package yesman.epicfight.world.capabilities.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.ArmorCapability;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.DefinedWeaponTypes;
import yesman.epicfight.world.capabilities.item.NBTSeparativeCapability;

public class ProviderItem implements ICapabilityProvider, NonNullSupplier<CapabilityItem> {
	private static final Map<Class<? extends Item>, Function<Item, CapabilityItem>> CAPABILITY_BY_CLASS = new HashMap<Class<? extends Item>, Function<Item, CapabilityItem>> ();
	private static final Map<Item, CapabilityItem> CAPABILITIES = new HashMap<Item, CapabilityItem> ();
	
	public static void registerWeaponTypesByClass() {
		CAPABILITY_BY_CLASS.put(ArmorItem.class, ArmorCapability::new);
		CAPABILITY_BY_CLASS.put(ShieldItem.class, DefinedWeaponTypes.SHIELD);
		CAPABILITY_BY_CLASS.put(SwordItem.class, DefinedWeaponTypes.SWORD);
		CAPABILITY_BY_CLASS.put(PickaxeItem.class, DefinedWeaponTypes.PICKAXE);
		CAPABILITY_BY_CLASS.put(AxeItem.class, DefinedWeaponTypes.AXE);
		CAPABILITY_BY_CLASS.put(ShovelItem.class, DefinedWeaponTypes.SHOVEL);
		CAPABILITY_BY_CLASS.put(HoeItem.class, DefinedWeaponTypes.HOE);
		CAPABILITY_BY_CLASS.put(BowItem.class, DefinedWeaponTypes.BOW);
		CAPABILITY_BY_CLASS.put(CrossbowItem.class, DefinedWeaponTypes.CROSSBOW);
	}
	
	public static void put(Item item, CapabilityItem cap) {
		CAPABILITIES.put(item, cap);
	}
	
	public static boolean has(Item item) {
		return CAPABILITIES.containsKey(item);
	}
	
	public static void clear() {
		CAPABILITIES.clear();
	}
	
	public static void addDefaultItems() {
		for (Item item : ForgeRegistries.ITEMS.getValues()) {
			if (!CAPABILITIES.containsKey(item)) {
				Class<?> clazz = item.getClass();
				CapabilityItem capability = null;
				
				for (; clazz != null && capability == null; clazz = clazz.getSuperclass()) {
					capability = CAPABILITY_BY_CLASS.getOrDefault(clazz, (argIn) -> null).apply(item);
				}
				
				if (capability != null) {
					EpicFightMod.LOGGER.info("register weapon capability for " + item);
					CAPABILITIES.put(item, capability);
				}
			}
		}
	}
	
	private CapabilityItem capability;
	private LazyOptional<CapabilityItem> optional = LazyOptional.of(this);
	
	public ProviderItem(ItemStack itemstack) {
		this.capability = CAPABILITIES.get(itemstack.getItem());
		if (this.capability instanceof NBTSeparativeCapability) {
			this.capability = this.capability.getFinal(itemstack);
		}
	}
	
	public boolean hasCapability() {
		return this.capability != null;
	}
	
	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		return cap == EpicFightCapabilities.CAPABILITY_ITEM ? this.optional.cast() : LazyOptional.empty();
	}
	
	@Override
	public CapabilityItem get() {
		return this.capability;
	}
}