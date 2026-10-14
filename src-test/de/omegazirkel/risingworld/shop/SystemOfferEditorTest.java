package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SystemOfferEditorTest {
    @Test
    public void factionFileNameIsAsciiSafeForLocalizedTraderNames() throws Exception {
        Path directory = Files.createTempDirectory("faction-trader-umlaut");
        String name = SystemOfferEditor.factionFileName("QNA Team", "Händlerin Mara");
        try {
            assertEquals("faction.QNA Team.Haendlerin Mara.json", name);
            assertTrue(new SystemOfferEditor(directory).createEmpty(name));
            assertTrue(Files.isRegularFile(directory.resolve(name)));
        } finally {
            Files.deleteIfExists(directory.resolve(name));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void factionFilesRejectCollisionsAndStockEdits() throws Exception {
        Path directory = Files.createTempDirectory("faction-trader-offers");
        String original = SystemOfferEditor.factionFileName("Die 9. Brigade", "Theresa");
        String replacement = SystemOfferEditor.factionFileName("Die 9. Brigade", "Mara");
        Path catalog = directory.resolve("system-offers.complete.json");
        try {
            SystemOfferEditor editor = new SystemOfferEditor(directory);
            Map<String, Object> preset = SystemOfferEditor.defaultOffer("stone", 0);
            SystemOfferFile.writeObjects(catalog, List.of(preset));
            assertTrue(editor.createEmpty(original));
            assertFalse(editor.createEmpty(original.toUpperCase()));
            assertEquals(SystemOfferEditor.AddResult.ADDED, editor.addFromCatalog(original, "stone", 0));
            Map<String, Object> saved = SystemOfferFile.readObjects(directory.resolve(original)).get(0);
            assertEquals("PLAYER_SUPPLIED", saved.get("stockMode"));
            assertEquals(0, ((Number) saved.get("targetStock")).intValue());
            assertTrue(editor.update(original, "stone.0", Map.of("stockLimit", 250L)));
            assertEquals(250L, ((Number) SystemOfferFile.readObjects(directory.resolve(original)).get(0)
                    .get("stockLimit")).longValue());
            assertEquals(0, ((Number) SystemOfferFile.readObjects(directory.resolve(original)).get(0)
                    .get("targetStock")).intValue());
            assertFalse(editor.update(original, "stone.0", Map.of("stock", 50)));
            assertFalse(editor.update(original, "stone.0", Map.of("targetStock", 25)));
            assertFalse(editor.update(original, "stone.0", Map.of("stockLimit", -1)));
            assertFalse(editor.update(original, "stone.0", Map.of("stockLimit", 2.5d)));
            assertTrue(editor.rename(original, replacement));
            assertFalse(Files.exists(directory.resolve(original)));
            assertTrue(Files.exists(directory.resolve(replacement)));
            assertFalse(editor.rename(replacement, "../outside.json"));
        } finally {
            Files.deleteIfExists(directory.resolve(original));
            Files.deleteIfExists(directory.resolve(replacement));
            Files.deleteIfExists(catalog);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void factionOfferRemovalDeletesStockWithoutAnyWalletSettlement() throws Exception {
        Path directory = Files.createTempDirectory("faction-offer-removal");
        String name = "faction.Test.Mara.json";
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SystemOfferEditor editor = new SystemOfferEditor(directory);
            assertTrue(editor.createEmpty(name));
            SystemOfferFile.writeObjects(directory.resolve(name), List.of(SystemOfferEditor.defaultOffer("stone", 0)));
            ShopEconomyStore economy = new ShopEconomyStore(connection);
            assertTrue(economy.configure("trader:47", "stone.0", 3L, 0.0d, 0.0d));
            assertTrue(editor.removeFactionOffer(name, "stone.0", economy, "trader:47"));
            assertTrue(SystemOfferFile.readObjects(directory.resolve(name)).isEmpty());
            assertFalse(economy.hasFactionState("trader:47", "stone.0"));
        } finally {
            Files.deleteIfExists(directory.resolve(name));
            Files.deleteIfExists(directory);
        }
    }
    @Test
    public void disabledCatalogItemUsesDefaultsAndPreservesEnabledPresets() throws Exception {
        Path directory = Files.createTempDirectory("shop-editor-catalog-test");
        Path file = directory.resolve("trader.json");
        Path catalog = directory.resolve("system-offers.complete.json");
        try {
            SystemOfferEditor editor = new SystemOfferEditor(directory);
            Map<String, Object> disabled = SystemOfferEditor.defaultOffer("newitem", 3);
            disabled.put("isEnabled", false);
            disabled.put("basePrice", 9999);
            disabled.put("stockMode", "STATIC");
            Map<String, Object> enabled = SystemOfferEditor.defaultOffer("preset", 0);
            enabled.put("basePrice", 42);
            SystemOfferFile.writeObjects(catalog, List.of(disabled, enabled));
            assertTrue(editor.createEmpty("trader.json"));
            assertEquals(SystemOfferEditor.AddResult.ADDED, editor.addFromCatalog("trader.json", "newitem", 3));
            assertEquals(SystemOfferEditor.AddResult.ADDED, editor.addFromCatalog("trader.json", "preset", 0));
            assertEquals(SystemOfferEditor.AddResult.DUPLICATE, editor.addFromCatalog("trader.json", "newitem", 3));
            List<Map<String, Object>> saved = SystemOfferFile.readObjects(file);
            assertEquals("HYBRID", saved.get(0).get("stockMode"));
            assertEquals(10, ((Number) saved.get(0).get("basePrice")).intValue());
            assertEquals(0, ((Number) saved.get(0).get("stock")).intValue());
            assertEquals(42, ((Number) saved.get(1).get("basePrice")).intValue());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(catalog);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void removalIdentitySurvivesRetryButChangesAfterReaddingOffer() throws Exception {
        Path directory = Files.createTempDirectory("shop-editor-test");
        Path file = directory.resolve("trader.json");
        try {
            SystemOfferEditor editor = new SystemOfferEditor(directory);
            Map<String, Object> offer = SystemOfferEditor.defaultOffer("customitem", 7);
            SystemOfferFile.writeObjects(file, List.of(offer));
            String first = editor.prepareRemoval("trader.json", "customitem.7");
            assertNotNull(first);
            assertEquals(first, new SystemOfferEditor(directory).prepareRemoval("trader.json", "customitem.7"));
            assertNull(editor.prepareRemoval("trader.json", "missing"));
            assertTrue(editor.remove("trader.json", "customitem.7"));
            assertTrue(SystemOfferFile.readObjects(file).isEmpty());
            SystemOfferFile.writeObjects(file, List.of(offer));
            assertNotEquals(first, editor.prepareRemoval("trader.json", "customitem.7"));
            assertNull(editor.prepareRemoval("../outside.json", "customitem.7"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void movePersistsFileOrderAndRejectsBoundaries() throws Exception {
        Path directory = Files.createTempDirectory("shop-editor-order-test");
        Path file = directory.resolve("trader.json");
        try {
            SystemOfferEditor editor = new SystemOfferEditor(directory);
            SystemOfferFile.writeObjects(file, List.of(SystemOfferEditor.defaultOffer("first", 0),
                    SystemOfferEditor.defaultOffer("second", 0), SystemOfferEditor.defaultOffer("third", 0)));
            assertTrue(editor.move("trader.json", "second.0", -1));
            assertEquals("second.0", SystemOfferFile.readObjects(file).get(0).get("id"));
            assertTrue(editor.move("trader.json", "second.0", 1));
            assertFalse(editor.move("trader.json", "first.0", -1));
            assertFalse(editor.move("trader.json", "third.0", 1));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
