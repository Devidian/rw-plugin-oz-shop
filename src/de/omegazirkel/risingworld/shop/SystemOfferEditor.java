package de.omegazirkel.risingworld.shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.risingworld.api.definitions.Definitions;

import de.omegazirkel.risingworld.Shop;

/** Safe, file-local mutations for administrator-managed system offers. */
public final class SystemOfferEditor {
    public enum AddResult { ADDED, TARGET_READ_ONLY, DUPLICATE, NOT_IN_CATALOG, FAILED }
    private final Shop plugin;
    private final Path offerRoot;

    public SystemOfferEditor(Shop plugin) { this.plugin = plugin; this.offerRoot = null; }

    SystemOfferEditor(Path offerRoot) { this.plugin = null; this.offerRoot = offerRoot; }

    public boolean exists(String name) {
        Path file = file(name);
        return file != null && Files.exists(file);
    }

    public boolean createEmpty(String name) {
        Path file = file(name);
        if (file == null || collides(name, null)) return false;
        try { SystemOfferFile.writeObjects(file, List.of()); return true; }
        catch (IOException ex) { Shop.logger().error("Could not create offer file: " + ex.getMessage()); return false; }
    }

    public boolean deleteEmpty(String name) {
        Path target = file(name);
        if (target == null || !Files.isRegularFile(target)) return false;
        try {
            if (!SystemOfferFile.readObjects(target).isEmpty()) return false;
            return Files.deleteIfExists(target);
        } catch (IOException ex) { return false; }
    }

    public boolean deleteFactionFile(String name) {
        Path target = file(name);
        if (target == null || !name.startsWith("faction.") || !Files.isRegularFile(target)) return false;
        try { return Files.deleteIfExists(target); }
        catch (IOException ex) { Shop.logger().error("Could not delete faction trader offer file: " + ex.getMessage()); return false; }
    }

    public static String factionFileName(String factionName, String traderName) {
        return "faction." + safePart(factionName) + "." + safePart(traderName) + ".json";
    }

    private static String safePart(String name) {
        String value = name == null ? "" : name.trim().replace("Ä", "Ae").replace("Ö", "Oe")
                .replace("Ü", "Ue").replace("ä", "ae").replace("ö", "oe")
                .replace("ü", "ue").replace("ß", "ss");
        String result = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").replaceAll("[^A-Za-z0-9 _-]", "_");
        return result.isBlank() ? "unnamed" : result;
    }

    public boolean collides(String name, String currentName) {
        Path candidate = file(name);
        if (candidate == null) return true;
        Path root = candidate.getParent();
        try (var files = Files.list(root)) {
            return files.anyMatch(path -> path.getFileName().toString().equalsIgnoreCase(name)
                    && (currentName == null || !path.getFileName().toString().equalsIgnoreCase(currentName)));
        } catch (IOException ex) { return true; }
    }

    public boolean rename(String oldName, String newName) {
        Path oldFile = file(oldName);
        Path newFile = file(newName);
        if (oldFile == null || newFile == null || !Files.isRegularFile(oldFile) || collides(newName, oldName)) return false;
        if (oldFile.equals(newFile)) return true;
        try {
            Files.move(oldFile, newFile, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            try { Files.move(oldFile, newFile); return true; }
            catch (IOException failure) { Shop.logger().error("Could not rename faction trader file: " + failure.getMessage()); }
        } catch (IOException ex) { Shop.logger().error("Could not rename faction trader file: " + ex.getMessage()); }
        return false;
    }

    public AddResult addFromCatalog(String targetName, String itemName, int variant) {
        Path target = editableFile(targetName);
        if (target == null) return AddResult.FAILED;
        if (!Files.isWritable(target)) return AddResult.TARGET_READ_ONLY;
        Path catalog = file("system-offers.complete.json");
        try {
            List<Map<String, Object>> targetOffers = SystemOfferFile.readObjects(target);
            for (Map<String, Object> offer : targetOffers) if (matches(offer, itemName, variant)) return AddResult.DUPLICATE;
            for (Map<String, Object> offer : Files.exists(catalog) ? SystemOfferFile.readObjects(catalog) : List.<Map<String, Object>>of()) {
                if (matches(offer, itemName, variant)) {
                    Map<String, Object> copy = Boolean.FALSE.equals(offer.get("isEnabled"))
                            ? defaultOffer(itemName, variant) : new LinkedHashMap<>(offer);
                    if (targetName.startsWith("faction.")) constrainFactionOffer(copy);
                    copy.put("isEnabled", Boolean.TRUE);
                    copy.remove("_removalId");
                    targetOffers.add(copy);
                    SystemOfferFile.writeObjects(target, targetOffers);
                    return AddResult.ADDED;
                }
            }
            if (itemName == null || itemName.isBlank() || variant < 0
                    || (Definitions.getItemDefinition(itemName) == null
                    && Definitions.getObjectDefinition(itemName) == null
                    && Definitions.getConstructionDefinition(itemName) == null
                    && Definitions.getClothingDefinition(itemName) == null
                    && Definitions.getPlantDefinition(itemName) == null)) return AddResult.NOT_IN_CATALOG;
            Map<String, Object> created = defaultOffer(itemName, variant);
            if (targetName.startsWith("faction.")) constrainFactionOffer(created);
            targetOffers.add(created);
            SystemOfferFile.writeObjects(target, targetOffers);
            return AddResult.ADDED;
        } catch (java.nio.file.AccessDeniedException ex) {
            Shop.logger().warn("Offer file is read-only: " + target);
            return AddResult.TARGET_READ_ONLY;
        } catch (IOException | IllegalArgumentException ex) { Shop.logger().error("Could not add system offer: " + ex.getMessage()); }
        return AddResult.FAILED;
    }

    static Map<String, Object> defaultOffer(String itemName, int variant) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("id", itemName + "." + variant);
        offer.put("itemName", itemName);
        offer.put("itemVariant", variant);
        offer.put("amount", 1);
        offer.put("isEnabled", true);
        offer.put("stock", 0);
        offer.put("targetStock", 10);
        offer.put("stockLimit", 100);
        offer.put("stockMode", "HYBRID");
        offer.put("drainPercent", 50);
        offer.put("drainMax", 5);
        offer.put("restockMax", 2);
        offer.put("basePrice", 10);
        offer.put("minPriceMultiplier", 0.25d);
        offer.put("maxPriceMultiplier", 4);
        offer.put("spreadPercent", 25);
        offer.put("perPlayerDailySellLimit", 1000);
        offer.put("globalDailySellLimit", 10000);
        return offer;
    }

    private static void constrainFactionOffer(Map<String, Object> offer) {
        offer.put("stock", 0);
        offer.put("targetStock", 0);
        offer.put("stockMode", "PLAYER_SUPPLIED");
        offer.put("drainRate", 0);
        offer.put("refillRate", 0);
        offer.put("drainPercent", 0);
        offer.put("drainMax", 0);
        offer.put("restockPercent", 0);
        offer.put("restockMax", 0);
    }

    /** Persists a retry-stable settlement identity before any Wallet mutation. */
    public String prepareRemoval(String targetName, String offerId) {
        Path target = editableFile(targetName);
        if (target == null || !Files.isWritable(target)) return null;
        try {
            List<Map<String, Object>> offers = SystemOfferFile.readObjects(target);
            for (Map<String, Object> offer : offers) {
                if (!offerId.equalsIgnoreCase(String.valueOf(offer.get("id")))) continue;
                Object existing = offer.get("_removalId");
                if (existing instanceof String id && !id.isBlank()) return id;
                String id = UUID.randomUUID().toString();
                offer.put("_removalId", id);
                SystemOfferFile.writeObjects(target, offers);
                return id;
            }
        } catch (IOException | IllegalArgumentException ex) {
            Shop.logger().error("Could not prepare offer removal: " + ex.getMessage());
        }
        return null;
    }

    public boolean remove(String targetName, String offerId) { return mutate(targetName, offerId, null, true); }

    /** Writes the file first, then deletes stock; restores the file if the database transaction fails. */
    public boolean removeFactionOffer(String targetName, String offerId, ShopEconomyStore economy, String scope) {
        Path target = editableFile(targetName);
        if (target == null || !targetName.startsWith("faction.") || economy == null) return false;
        Path staged = target.resolveSibling(target.getFileName() + ".staged");
        try {
            String original = Files.readString(target);
            List<Map<String, Object>> offers = new ArrayList<>(SystemOfferFile.readObjects(target));
            if (!offers.removeIf(offer -> offerId.equalsIgnoreCase(String.valueOf(offer.get("id"))))) return false;
            SystemOfferFile.writeObjects(staged, offers);
            try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (economy.deleteOffer(scope, offerId)) return true;
            Files.writeString(staged, original);
            try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) { Shop.logger().error("Faction trader offer removal failed: " + ex.getMessage()); }
        finally { try { Files.deleteIfExists(staged); } catch (IOException ignored) { } }
        return false;
    }

    public boolean update(String targetName, String offerId, Map<String, Object> values) {
        if (targetName != null && targetName.startsWith("faction.") && values != null && values.keySet().stream()
                .anyMatch(key -> java.util.Set.of("stock", "targetStock", "stockMode", "drainRate",
                        "refillRate", "drainPercent", "drainMax", "restockPercent", "restockMax").contains(key))) return false;
        if (targetName != null && targetName.startsWith("faction.") && values != null
                && values.containsKey("stockLimit") && !validStockLimit(values.get("stockLimit"))) return false;
        return mutate(targetName, offerId, values, false);
    }

    private static boolean validStockLimit(Object value) {
        return (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                && ((Number) value).longValue() >= 0L;
    }

    /** Moves an offer one position in its editable JSON file without changing its data. */
    public boolean move(String targetName, String offerId, int direction) {
        if (direction != -1 && direction != 1) return false;
        Path target = editableFile(targetName);
        if (target == null || offerId == null) return false;
        try {
            List<Map<String, Object>> offers = new ArrayList<>(SystemOfferFile.readObjects(target));
            for (int index = 0; index < offers.size(); index++) {
                if (!offerId.equalsIgnoreCase(String.valueOf(offers.get(index).get("id")))) continue;
                int targetIndex = index + direction;
                if (targetIndex < 0 || targetIndex >= offers.size()) return false;
                Map<String, Object> current = offers.remove(index);
                offers.add(targetIndex, current);
                SystemOfferFile.writeObjects(target, offers);
                return true;
            }
        } catch (IOException | IllegalArgumentException ex) {
            Shop.logger().error("Could not reorder system offer: " + ex.getMessage());
        }
        return false;
    }

    private boolean mutate(String targetName, String offerId, Map<String, Object> values, boolean remove) {
        Path target = editableFile(targetName);
        if (target == null || offerId == null) return false;
        try {
            List<Map<String, Object>> offers = new ArrayList<>(SystemOfferFile.readObjects(target));
            for (int i = 0; i < offers.size(); i++) if (offerId.equalsIgnoreCase(String.valueOf(offers.get(i).get("id")))) {
                if (remove) offers.remove(i); else { offers.get(i).putAll(values); offers.get(i).put("isEnabled", Boolean.TRUE); }
                SystemOfferFile.writeObjects(target, offers); return true;
            }
        } catch (IOException | IllegalArgumentException ex) { Shop.logger().error("Could not edit system offer: " + ex.getMessage()); }
        return false;
    }

    private Path editableFile(String name) {
        Path file = file(name);
        if (file == null || file.getFileName().toString().equalsIgnoreCase("system-offers.default.json")) return null;
        return Files.exists(file) ? file : null;
    }

    private Path file(String name) {
        if (name == null || name.isBlank() || !name.toLowerCase().endsWith(".json")) return null;
        Path root = offerRoot != null ? offerRoot : SystemOfferFile.offerFile(plugin, "placeholder.json").getParent();
        Path candidate = root.resolve(name.trim()).normalize();
        return candidate.getParent() != null && candidate.getParent().equals(root) ? candidate : null;
    }
    private static boolean matches(Map<String, Object> offer, String itemName, int variant) {
        return itemName != null && itemName.equalsIgnoreCase(String.valueOf(offer.get("itemName")))
                && variant == number(offer.get("itemVariant"));
    }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); }
}
