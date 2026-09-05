package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SystemOfferEditorTest {
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
}
