package de.omegazirkel.risingworld.shop;

import java.util.List;
import java.util.Random;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Npcs;
import net.risingworld.api.objects.Npc;
import net.risingworld.api.objects.Skin;

/** Generation and application of a trader's visual appearance. */
public final class TraderAppearance {
    private static final int[] LIGHT_SKIN_COLORS = { 0xFFE0BD, 0xF1C27D, 0xE0AC69, 0xFFDBAC };
    private static final int[] MEDIUM_SKIN_COLORS = { 0xC68642, 0xA56B46, 0x8D5524 };
    private static final int[] DARK_SKIN_COLORS = { 0x6F4E37, 0x4B2E20 };
    private static final int[] HAIR_COLORS = { 0x1C120C, 0x3A2518, 0x5A381E, 0x8B5A2B, 0xC48A4A, 0xD9B36C };
    private static final int[] EYE_COLORS = { 0x4E7AA8, 0x5A8E50, 0x6B4A2E, 0x7D8B44 };
    private static final int MALE_HAIRSTYLE_FIRST = 50;
    private static final int MALE_HAIRSTYLE_LAST = 68;
    private static final int FEMALE_HAIRSTYLE_FIRST = 100;
    private static final int FEMALE_HAIRSTYLE_LAST = 119;

    public record Appearance(String name, boolean male, List<String> clothing,
            int skinColor, int hairColor, int eyeColor, byte hairstyle, byte beard, byte variation) { }

    private TraderAppearance() { }

    public static Appearance generate(TraderGeneratorConfig config, String prefix, boolean male, Random random) {
        String name = prefix + " " + config.randomName(male, random);
        List<String> outfit = config.randomOutfit(random);
        return new Appearance(name, male, List.copyOf(outfit),
                randomSkinColor(random), random(HAIR_COLORS, random), random(EYE_COLORS, random),
                randomHairstyle(male, random),
                (byte) (male && random.nextInt(100) >= 20 ? random.nextInt(14) : -1),
                (byte) random.nextInt(5));
    }

    public static void initialize(Npc npc, Appearance appearance, TraderService traderService) {
        if (npc == null || npc.isDead()) return;
        verifyAppearance(npc, appearance, traderService);
        byte[] saved = traderService == null ? null : traderService.clothing(npc.getGlobalID());
        if (saved != null) npc.getClothes().deserialize(saved);
        else applyTraderOutfit(npc, appearance.clothing());
    }

    public static void applyAppearance(Npc npc, Appearance appearance, TraderService traderService) {
        npc.setName(appearance.name());
        npc.setLocked(true);
        if (traderService != null) traderService.applyNpcFlags(npc);
        Skin skin = npc.getSkin();
        if (skin != null) {
            skin.setGender(appearance.male() ? Skin.Gender.Male : Skin.Gender.Female);
            skin.setSkinColor(appearance.skinColor());
            skin.setHairColor(appearance.hairColor());
            skin.setEyeColor(appearance.eyeColor());
            skin.setHairstyle(appearance.hairstyle());
            skin.setBeard(appearance.beard());
            skin.setVariation(appearance.variation());
        }
    }

    public static void verifyAppearance(Npc npc, Appearance appearance, TraderService traderService) {
        if (npc == null || npc.isDead() || generatedTraderAppearanceMatches(npc, appearance)) return;
        Shop.logger().warn("Repairing generated trader appearance for NPC " + npc.getGlobalID()
                + ": expected name '" + appearance.name() + "', actual name '" + npc.getName() + "'");
        applyAppearance(npc, appearance, traderService);
        if (!generatedTraderAppearanceMatches(npc, appearance)) {
            Shop.logger().warn("Generated trader appearance is still not fully applied for NPC " + npc.getGlobalID());
        }
    }

    private static boolean generatedTraderAppearanceMatches(Npc npc, Appearance appearance) {
        if (!appearance.name().equals(npc.getName()) || !npc.isLocked() || !npc.isInvincible()) return false;
        Skin skin = npc.getSkin();
        return npc.getBehaviour() == Npcs.Behaviour.Dummy && !npc.isStatic() && skin != null
                && skin.getGender() == (appearance.male() ? Skin.Gender.Male : Skin.Gender.Female)
                && skin.getSkinColor() == appearance.skinColor()
                && skin.getHairColor() == appearance.hairColor()
                && skin.getEyeColor() == appearance.eyeColor()
                && skin.getHairstyle() == appearance.hairstyle()
                && skin.getBeard() == appearance.beard()
                && skin.getVariation() == appearance.variation();
    }

    private static int randomSkinColor(Random random) {
        int weight = random.nextInt(100);
        return random(weight < 76 ? LIGHT_SKIN_COLORS : weight < 96 ? MEDIUM_SKIN_COLORS : DARK_SKIN_COLORS, random);
    }

    public static byte randomHairstyle(boolean male, Random random) {
        int first = male ? MALE_HAIRSTYLE_FIRST : FEMALE_HAIRSTYLE_FIRST;
        int last = male ? MALE_HAIRSTYLE_LAST : FEMALE_HAIRSTYLE_LAST;
        return (byte) (first + random.nextInt(last - first + 1));
    }

    private static int random(int[] values, Random random) {
        return values[random.nextInt(values.length)];
    }

    public static void applyTraderOutfit(Npc npc, List<String> clothing) {
        net.risingworld.api.objects.Clothes clothes = npc.getClothes();
        if (clothes == null) {
            Shop.logger().warn("Generated trader NPC " + npc.getGlobalID() + " does not expose a Clothes object");
            return;
        }
        clothes.removeAll();
        for (String garment : clothing) {
            ClothingDefinition definition = Definitions.getClothingDefinition(garment);
            if (definition == null) {
                Shop.logger().warn("Ignoring unknown trader clothing definition: " + garment);
                continue;
            }
            clothes.add((short) definition.id);
        }
    }

}
