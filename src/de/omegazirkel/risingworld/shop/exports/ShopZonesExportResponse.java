package de.omegazirkel.risingworld.shop.exports;

import java.util.List;

public record ShopZonesExportResponse(
        int schemaVersion,
        List<ShopZoneExport> zones) {

    public ShopZonesExportResponse {
        zones = List.copyOf(zones);
    }
}
