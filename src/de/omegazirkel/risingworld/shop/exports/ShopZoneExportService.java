package de.omegazirkel.risingworld.shop.exports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ShopZoneExportService {
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;

    public ShopZoneExportService(Connection connection) {
        this.connection = connection;
    }

    public ShopZonesExportResponse exportZones(Long lastChange) throws SQLException {
        long cursor = lastChange == null ? -1L : lastChange.longValue();
        List<ShopZoneExport> zones = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT area_id, area_name, created_by, created_at, system_shop, system_offers_file
                FROM shop_zones
                WHERE created_at > ?
                ORDER BY created_at DESC, area_id DESC;
                """)) {
            statement.setLong(1, cursor);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    zones.add(new ShopZoneExport(
                            result.getLong("area_id"),
                            result.getString("area_name"),
                            result.getString("created_by"),
                            result.getLong("created_at"),
                            result.getInt("system_shop"),
                            result.getString("system_offers_file")));
                }
            }
        }
        return new ShopZonesExportResponse(SCHEMA_VERSION, zones);
    }
}
