package de.omegazirkel.risingworld.shop;

public class ShopZone {
    private final long areaId;
    private final String areaName;
    private final String createdBy;
    private final long createdAt;
    private final int systemShop;
    private final String systemOffersFile;

    public ShopZone(long areaId, String areaName, String createdBy, long createdAt) {
        this(areaId, areaName, createdBy, createdAt, -1, "");
    }

    public ShopZone(long areaId, String areaName, String createdBy, long createdAt, int systemShop) {
        this(areaId, areaName, createdBy, createdAt, systemShop, "");
    }

    public ShopZone(long areaId, String areaName, String createdBy, long createdAt, int systemShop,
            String systemOffersFile) {
        this.areaId = areaId;
        this.areaName = areaName == null ? "" : areaName.trim();
        this.createdBy = createdBy == null ? "" : createdBy.trim();
        this.createdAt = createdAt;
        this.systemShop = normalizeMode(systemShop);
        this.systemOffersFile = systemOffersFile == null ? "" : systemOffersFile.trim();
    }

    public long getAreaId() { return areaId; }
    public String getAreaName() { return areaName; }
    public String getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
    public int getSystemShop() { return systemShop; }
    public String getSystemOffersFile() { return systemOffersFile; }

    public boolean systemShopEnabled(boolean globalEnabled) {
        return systemShop == -1 ? globalEnabled : systemShop == 1;
    }

    public static int normalizeMode(int mode) {
        if (mode < -1) {
            return -1;
        }
        if (mode > 1) {
            return 1;
        }
        return mode;
    }
}
