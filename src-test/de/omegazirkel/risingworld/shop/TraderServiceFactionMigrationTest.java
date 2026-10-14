package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.Test;

public class TraderServiceFactionMigrationTest {
    @Test
    public void existingTraderRecordsReceiveZeroFactionId() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE shop_traders(npc_id BIGINT PRIMARY KEY,name TEXT NOT NULL,created_by TEXT NOT NULL,
                        created_at BIGINT NOT NULL,system_offers_file TEXT NOT NULL DEFAULT 'default-trader.json',
                        plugin_shop_enabled INTEGER NOT NULL DEFAULT 1)
                        """);
                statement.execute("INSERT INTO shop_traders(npc_id,name,created_by,created_at) VALUES(1,'Old','Admin',1)");
            }
            TraderService traders = new TraderService(connection);
            Trader old = traders.find(1L).orElseThrow();
            assertEquals(0, old.factionId());
            assertEquals(0, traders.factionTraderCount(12));
            assertFalse(traders.factionTraderNameExists(12, "Old", 0));
        }
    }
}
