package de.omegazirkel.risingworld.shop;

public class ShopZone {
    private final long areaId;
    private final String areaName;
    private final String createdBy;
    private final long createdAt;
    private final int systemShop;

    public ShopZone(long areaId, String areaName, String createdBy, long createdAt) {
        this(areaId, areaName, createdBy, createdAt, -1);
    }

    public ShopZone(long areaId, String areaName, String createdBy, long createdAt, int systemShop) {
        this.areaId = areaId;
        this.areaName = areaName == null ? "" : areaName.trim();
        this.createdBy = createdBy == null ? "" : createdBy.trim();
        this.createdAt = createdAt;
        this.systemShop = normalizeMode(systemShop);
    }

    public long getAreaId() { return areaId; }
    public String getAreaName() { return areaName; }
    public String getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
    public int getSystemShop() { return systemShop; }

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
