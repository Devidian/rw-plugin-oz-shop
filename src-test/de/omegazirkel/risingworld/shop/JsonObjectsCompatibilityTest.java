package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class JsonObjectsCompatibilityTest {
    private static List<Map<String, Object>> parse(Class<?> owner, String input) throws Exception {
        return new JsonObjects(input, owner == ShopZoneService.class).parseArray();
    }

    @Test public void zonesAndOffersKeepTheirExistingNumberAndBlankRules() throws Exception {
        assertTrue(parse(ShopZoneService.class, "  ").isEmpty());
        expectInvalid(SystemOfferFile.class, "  ");
        assertEquals(Long.valueOf(42), parse(ShopZoneService.class, "[{\"id\":42}]").get(0).get("id"));
        assertEquals(Double.valueOf(42), parse(SystemOfferFile.class, "[{\"id\":42}]").get(0).get("id"));
        assertEquals(Double.valueOf(2.5), parse(SystemOfferFile.class, "[{\"price\":2.5}]").get(0).get("price"));
        expectInvalid(ShopZoneService.class, "[{\"price\":2.5}]");
        expectInvalid(ShopZoneService.class, "[{\"id\":9223372036854775808}]");
        expectInvalid(SystemOfferFile.class, "[{\"id\":9223372036854775808}]");
    }

    @Test public void bothKeepEscapeDuplicateAndTrailingInputBehavior() throws Exception {
        for (Class<?> owner : List.of(ShopZoneService.class, SystemOfferFile.class)) {
            var result = parse(owner, "[{\"name\":\"first\",\"name\":\"last\\nline\",\"x\":true,\"y\":null}] trailing");
            assertEquals("last\nline", result.get(0).get("name"));
            assertEquals(Boolean.TRUE, result.get(0).get("x"));
            assertTrue(result.get(0).containsKey("y"));
            assertEquals("u0041", parse(owner, "[{\"value\":\"\\u0041\"}]").get(0).get("value"));
            expectInvalid(owner, "[{\"name\":\"unterminated");
            expectInvalid(owner, "[{\"x\":}]");
            expectInvalid(owner, "[1]");
        }
    }

    @Test public void existingOfferEditorRoundTripsItsFields() throws Exception {
        Path directory = Files.createTempDirectory("shop-offer-objects");
        Path file = directory.resolve("offers.json");
        try {
            List<Map<String, Object>> objects = List.of(Map.of(
                    "id", "axe", "amount", 2, "price", 4.5, "enabled", true));
            SystemOfferFile.writeObjects(file, objects);
            Map<String, Object> restored = SystemOfferFile.readObjects(file).get(0);
            assertEquals("axe", restored.get("id"));
            assertEquals(Double.valueOf(2), restored.get("amount"));
            assertEquals(Double.valueOf(4.5), restored.get("price"));
            assertEquals(Boolean.TRUE, restored.get("enabled"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test public void malformedInputKeepsOwnerSpecificErrorOffsets() throws Exception {
        for (Class<?> owner : List.of(ShopZoneService.class, SystemOfferFile.class)) {
            try {
                parse(owner, "[{}");
                fail("expected missing closing bracket");
            } catch (IllegalArgumentException expected) {
                assertEquals(owner == ShopZoneService.class
                        ? "Expected ']' at index 3" : "Expected ']' at offset 3", expected.getMessage());
            }
        }
    }

    private static void expectInvalid(Class<?> owner, String input) throws Exception {
        try {
            parse(owner, input);
            fail("expected invalid input");
        } catch (IllegalArgumentException expected) {
            // Existing parsing failure is part of the import contract.
        }
    }
}
