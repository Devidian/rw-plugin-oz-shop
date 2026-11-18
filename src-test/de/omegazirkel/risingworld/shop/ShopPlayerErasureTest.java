package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Test;

public class ShopPlayerErasureTest {
    @Test public void deletesOnlyPlayerCountersAndRetainsGlobalTotals() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO shop_offer_daily_sell_counters "
                        + "(scope,offer_id,player_id,day_start,sold_amount,sold_value,updated_at) VALUES "
                        + "('global','offer','player:17',1,3,30,1),"
                        + "('global','offer','player:18',1,4,40,1),"
                        + "('global','offer','global',1,7,70,1)");
            }
            assertTrue(store.erasePlayerData(17));
            assertTrue(store.erasePlayerData(17));
            assertFalse(store.erasePlayerData(0));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM shop_offer_daily_sell_counters WHERE player_id='player:17'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM shop_offer_daily_sell_counters WHERE player_id='player:18'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM shop_offer_daily_sell_counters WHERE player_id='global'"));
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
}
