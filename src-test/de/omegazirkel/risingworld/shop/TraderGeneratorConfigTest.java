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
}
