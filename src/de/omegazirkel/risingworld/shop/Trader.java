package de.omegazirkel.risingworld.shop;

/** Persisted Shop-owned identity for one non-player trader. */
public record Trader(long npcId, String name, String createdBy, long createdAt, String systemOffersFile,
        boolean pluginShopEnabled) {
    public Trader {
        name = name == null ? "" : name.trim();
        createdBy = createdBy == null ? "" : createdBy.trim();
        systemOffersFile = systemOffersFile == null ? "" : systemOffersFile.trim();
    }

    public String accountId() {
        return "trader::" + npcId;
    }

    public String economyScope() {
        return "trader:" + npcId;
    }
}
