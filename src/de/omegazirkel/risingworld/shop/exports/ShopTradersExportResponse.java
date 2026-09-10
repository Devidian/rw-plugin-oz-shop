package de.omegazirkel.risingworld.shop.exports;
import java.util.List;
public record ShopTradersExportResponse(int schemaVersion, List<ShopTraderExport> traders) { }
