package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.Test;

public class TraderGeneratorConfigTest {
    @Test
    public void loadsOutfitsAndGenderNames() throws Exception {
        Path file = Files.createTempFile("trader-config", ".json");
        Files.writeString(file, "{\"clothing\":[[\"shirt\",\"pants\"]],\"names\":{\"male\":[\"Max\"],\"female\":[\"Mia\"]}}");
        TraderGeneratorConfig config = TraderGeneratorConfig.load(file);
        assertEquals(java.util.List.of("shirt", "pants"), config.randomOutfit(new Random(1)));
        assertEquals("Max", config.randomName(true, new Random(1)));
        assertEquals("Mia", config.randomName(false, new Random(1)));
    }

    @Test
    public void missingNamesFallBackToSafeDefaults() throws Exception {
        Path file = Files.createTempFile("trader-config", ".json");
        Files.writeString(file, "{\"clothing\":[],\"names\":{}}");
        TraderGeneratorConfig config = TraderGeneratorConfig.load(file);
        assertTrue(config.randomName(true, new Random()).length() > 0);
        assertTrue(config.randomName(false, new Random()).length() > 0);
    }

    @Test
    public void configuredHatsAreOptionalAndNeverDuplicateTheOutfitHat() throws Exception {
        Path file = Files.createTempFile("trader-config", ".json");
        Files.writeString(file, "{\"clothing\":[[\"shirt\",\"cap\"]],\"hats\":[\"cap\"],\"names\":{}}");
        TraderGeneratorConfig config = TraderGeneratorConfig.load(file);
        int hats = 0;
        for (int seed = 0; seed < 100; seed++) {
            java.util.List<String> outfit = config.randomOutfit(new Random(seed));
            assertTrue(outfit.size() <= 2);
            if (outfit.contains("cap")) hats++;
        }
        assertTrue(hats > 0 && hats < 100);
    }

}
