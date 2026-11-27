package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Random;

import org.junit.Test;

public class TraderAppearanceTest {
    @Test public void generatedAppearancePreservesSeededSelectionForBothGenders() {
        TraderGeneratorConfig config = new TraderGeneratorConfig(
                List.of(List.of("shirt", "hat"), List.of("coat", "boots")),
                List.of("hat"), List.of("Adam", "Bob"), List.of("Eva", "Fay"));
        int[] light = { 0xFFE0BD, 0xF1C27D, 0xE0AC69, 0xFFDBAC };
        int[] medium = { 0xC68642, 0xA56B46, 0x8D5524 };
        int[] dark = { 0x6F4E37, 0x4B2E20 };
        int[] hair = { 0x1C120C, 0x3A2518, 0x5A381E, 0x8B5A2B, 0xC48A4A, 0xD9B36C };
        int[] eyes = { 0x4E7AA8, 0x5A8E50, 0x6B4A2E, 0x7D8B44 };
        for (boolean male : new boolean[] { true, false }) {
            for (int seed = 0; seed < 100; seed++) {
                Random original = new Random(seed);
                String name = "Trader " + config.randomName(male, original);
                List<String> outfit = config.randomOutfit(original);
                int weight = original.nextInt(100);
                int[] skin = weight < 76 ? light : weight < 96 ? medium : dark;
                int expectedSkin = skin[original.nextInt(skin.length)];
                int expectedHair = hair[original.nextInt(hair.length)];
                int expectedEyes = eyes[original.nextInt(eyes.length)];
                byte style = (byte) ((male ? 50 : 100) + original.nextInt(male ? 19 : 20));
                byte beard = (byte) (male && original.nextInt(100) >= 20 ? original.nextInt(14) : -1);
                byte variation = (byte) original.nextInt(5);
                var actual = TraderAppearance.generate(config, "Trader", male, new Random(seed));
                assertEquals(name, actual.name());
                assertEquals(outfit, actual.clothing());
                assertEquals(expectedSkin, actual.skinColor());
                assertEquals(expectedHair, actual.hairColor());
                assertEquals(expectedEyes, actual.eyeColor());
                assertEquals(style, actual.hairstyle());
                assertEquals(beard, actual.beard());
                assertEquals(variation, actual.variation());
                assertEquals(male, actual.male());
            }
        }
    }

    @Test public void absentNpcIsIgnoredAtInitialization() {
        TraderAppearance.initialize(null, null, null);
    }
}
