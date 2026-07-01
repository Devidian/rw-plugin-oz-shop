package de.omegazirkel.risingworld.shop.exports;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.Test;

public class ShopZoneExportServiceTest {

    @Test
    public void exportsZones() throws Exception {
        try (Connection connection = database()) {
            seed(connection);

            ShopZonesExportResponse response = new ShopZoneExportService(connection).exportZones(null);

            assertEquals(1, response.schemaVersion());
            assertEquals(2, response.zones().size());
            assertEquals(42L, response.zones().get(0).areaId());
            assertEquals("Spawn Shop", response.zones().get(0).areaName());
            assertEquals("Alice", response.zones().get(0).createdBy());
            assertEquals(1, response.zones().get(0).systemShop());
            assertEquals("spawn-offers.json", response.zones().get(0).systemOffersFile());
            assertEquals(7L, response.zones().get(1).areaId());
        }
    }

    @Test
    public void filtersZonesByLastChange() throws Exception {
        try (Connection connection = database()) {
            seed(connection);

            ShopZonesExportResponse response = new ShopZoneExportService(connection).exportZones(1000L);

            assertEquals(1, response.zones().size());
            assertEquals(42L, response.zones().get(0).areaId());
        }
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE shop_zones (
                        area_id BIGINT PRIMARY KEY,
                        area_name TEXT NOT NULL,
                        created_by TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        system_shop INTEGER NOT NULL DEFAULT -1,
                        system_offers_file TEXT NOT NULL DEFAULT ''
                    );
                    """);
        }
        return connection;
    }

    private static void seed(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shop_zones
                (area_id, area_name, created_by, created_at, system_shop, system_offers_file)
                VALUES (7, 'Harbor Shop', 'Bob', 1000, -1, ''),
                       (42, 'Spawn Shop', 'Alice', 3000, 1, 'spawn-offers.json');
                """)) {
            statement.executeUpdate();
        }
    }
}
