package yesman.epicfight.world.capabilities.skill;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.HashMultimap;

import net.minecraft.nbt.CompoundTag;
import yesman.epicfight.gameasset.Skills;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillCategory;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

public class CapabilitySkill {
	public static final CapabilitySkill EMPTY = new CapabilitySkill(null);
	public final SkillContainer[] skillContainers;
	private final HashMultimap<SkillCategory, Skill> learnedSkills = HashMultimap.create();
	
	public CapabilitySkill(PlayerPatch<?> playerpatch) {
		SkillCategory[] categories = SkillCategory.values();
		this.skillContainers = new SkillContainer[SkillCategory.values().length];
		for (SkillCategory slot : categories) {
			this.skillContainers[slot.getIndex()] = new SkillContainer(playerpatch, slot.getIndex());
		}
	}
	
	public void addLearnedSkills(Skill skill) {
		SkillCategory category = skill.getCategory();
		if (!this.learnedSkills.containsKey(category) || !this.learnedSkills.get(category).contains(skill)) {
			this.learnedSkills.put(category,  skill);
		}
	}
	
	public Collection<Skill> getLearnedSkills(SkillCategory skillCategory) {
		return this.learnedSkills.get(skillCategory);
	}
	
	public boolean hasCategory(SkillCategory skillCategory) {
		return this.learnedSkills.containsKey(skillCategory);
	}
	
	public CompoundTag toNBT() {
		CompoundTag nbt = new CompoundTag();
		
		for (SkillContainer container : this.skillContainers) {
			if (container.getSkill() != null && container.getSkill().getCategory().shouldSaved()) {
				nbt.putString(String.valueOf(container.getSkill().getCategory().getIndex()), container.getSkill().getName());
			}
		}
		
		for (Map.Entry<SkillCategory, Collection<Skill>> entry : this.learnedSkills.asMap().entrySet()) {
			CompoundTag learnedNBT = new CompoundTag();
			int i = 0;
			for (Skill skill : entry.getValue()) {
				learnedNBT.putString(String.valueOf(i++), skill.getName());
			}
			nbt.put(String.valueOf("learned" + entry.getKey().getIndex()), learnedNBT);
		}
		
		return nbt;
	}
	
	public void fromNBT(CompoundTag nbt) {
		int i = 0;
		for (SkillContainer container : this.skillContainers) {
			if (nbt.contains(String.valueOf(i))) {
				Skill skill = Skills.findSkill(nbt.getString(String.valueOf(i)));
				container.setSkill(skill);
				this.addLearnedSkills(skill);
			}
			i++;
		}
		
		for (SkillCategory category : SkillCategory.values()) {
			if (nbt.contains("learned" + String.valueOf(category.getIndex()))) {
				CompoundTag learnedNBT = nbt.getCompound("learned" + String.valueOf(category.ordinal()));
				for (String key : learnedNBT.getAllKeys()) {
					this.addLearnedSkills(Skills.findSkill(learnedNBT.getString(key)));
				}
			}
		}
	}
}