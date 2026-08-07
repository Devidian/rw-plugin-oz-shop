package de.omegazirkel.risingworld.shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.omegazirkel.risingworld.Shop;

/** Safe, file-local mutations for administrator-managed system offers. */
public final class SystemOfferEditor {
    private final Shop plugin;

    public SystemOfferEditor(Shop plugin) { this.plugin = plugin; }

    public boolean exists(String name) { return file(name) != null && Files.exists(file(name)); }

    public boolean createEmpty(String name) {
        Path file = file(name);
        if (file == null || Files.exists(file)) return false;
        try { SystemOfferFile.writeObjects(file, List.of()); return true; }
        catch (IOException ex) { Shop.logger().error("Could not create offer file: " + ex.getMessage()); return false; }
    }

    public boolean addFromCatalog(String targetName, String itemName, int variant) {
        Path target = editableFile(targetName);
        if (target == null) return false;
        Path catalog = pluginPath().resolve("system-offers.complete.json");
        try {
            List<Map<String, Object>> targetOffers = SystemOfferFile.readObjects(target);
            for (Map<String, Object> offer : targetOffers) if (matches(offer, itemName, variant)) return false;
            for (Map<String, Object> offer : SystemOfferFile.readObjects(catalog)) {
                if (matches(offer, itemName, variant)) {
                    Map<String, Object> copy = new LinkedHashMap<>(offer);
                    copy.put("isEnabled", Boolean.TRUE);
                    targetOffers.add(copy);
                    SystemOfferFile.writeObjects(target, targetOffers);
                    return true;
                }
            }
        } catch (IOException | IllegalArgumentException ex) { Shop.logger().error("Could not add system offer: " + ex.getMessage()); }
        return false;
    }

    public boolean remove(String targetName, String offerId) { return mutate(targetName, offerId, null, true); }

    public boolean update(String targetName, String offerId, Map<String, Object> values) {
        return mutate(targetName, offerId, values, false);
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
        return file != null && Files.exists(file) && !file.getFileName().toString().equalsIgnoreCase("system-offers.default.json") ? file : null;
    }

    private Path file(String name) {
        if (name == null || name.isBlank() || !name.toLowerCase().endsWith(".json")) return null;
        Path root = pluginPath(); Path candidate = root.resolve(name.trim()).normalize();
        return candidate.getParent() != null && candidate.getParent().equals(root) ? candidate : null;
    }

    private Path pluginPath() { return Paths.get(plugin.getPath() == null ? "." : plugin.getPath()).toAbsolutePath().normalize(); }
    private static boolean matches(Map<String, Object> offer, String itemName, int variant) {
        return itemName != null && itemName.equalsIgnoreCase(String.valueOf(offer.get("itemName")))
                && variant == number(offer.get("itemVariant"));
    }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); }
}
