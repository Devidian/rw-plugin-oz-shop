package de.omegazirkel.risingworld.shop.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ShopZoneRouteTest {
    @Test
    public void acceptsOptionalCursorAndUnsignedLongValues() {
        assertNull(ShopZoneRoute.lastChange(null));
        assertEquals(Long.valueOf(42L), ShopZoneRoute.lastChange("42"));
    }

    @Test
    public void rejectsInvalidCursorValues() {
        assertInvalid("-1");
        assertInvalid("not-a-number");
        assertInvalid("9223372036854775808");
    }

    private void assertInvalid(String value) {
        try {
            ShopZoneRoute.lastChange(value);
            fail("Expected invalid cursor: " + value);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
