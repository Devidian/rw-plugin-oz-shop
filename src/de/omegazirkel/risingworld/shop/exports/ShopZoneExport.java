package de.omegazirkel.risingworld.shop.exports;

public record ShopZoneExport(
        long areaId,
        String areaName,
        String createdBy,
        long createdAt,
        int systemShop,
        String systemOffersFile) {
}
