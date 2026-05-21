package de.omegazirkel.risingworld.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Player;

public class ShopZoneService {
    private final Path zonesFile;
    private final Map<Long, ShopZone> zones = new LinkedHashMap<>();

    public ShopZoneService(Shop plugin, String configuredFileName) {
        Path pluginPath = Paths.get(plugin.getPath() != null ? plugin.getPath() : ".");
        this.zonesFile = pluginPath.resolve(configuredFileName == null || configuredFileName.isBlank()
                ? "shop-zones.json"
                : configuredFileName);
        load();
    }

    public synchronized void load() {
        zones.clear();
        if (Files.notExists(zonesFile)) {
            save();
            return;
        }
        try {
            String json = Files.readString(zonesFile, StandardCharsets.UTF_8);
            for (Map<String, Object> object : new JsonObjects(json).parseArray()) {
                ShopZone zone = fromJson(object);
                if (zone.getAreaId() > 0) {
                    zones.put(zone.getAreaId(), zone);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            Shop.logger().error("Could not load shop zones: " + ex.getMessage());
        }
    }

    public synchronized List<ShopZone> listZones() {
        return zones.values().stream()
                .sorted(Comparator.comparing(ShopZone::getAreaName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(ShopZone::getAreaId))
                .toList();
    }

    public synchronized Optional<ShopZone> zoneAt(Player player) {
        Area area = player.getCurrentArea();
        if (area == null) {
            return Optional.empty();
        }
        ShopZone zone = zones.get(area.getID());
        if (zone == null || Server.getArea(zone.getAreaId()) == null) {
            return Optional.empty();
        }
        return Optional.of(zone);
    }

    public synchronized boolean isInEnabledZone(Player player) {
        return zoneAt(player).isPresent();
    }

    public synchronized Optional<ShopZone> createOrEnableCurrentZone(Player player) {
        Area area = player.getCurrentArea();
        if (area == null || area.getID() <= 0) {
            return Optional.empty();
        }
        ShopZone existing = zones.get(area.getID());
        ShopZone zone = existing == null
                ? new ShopZone(area.getID(), areaName(area), player.getName(), System.currentTimeMillis())
                : new ShopZone(area.getID(), areaName(area), existing.getCreatedBy(), existing.getCreatedAt(),
                        existing.getSystemShop());
        zones.put(area.getID(), zone);
        save();
        return Optional.of(zone);
    }

    public synchronized Optional<ShopZone> setSystemShopMode(long areaId, int systemShop) {
        ShopZone existing = zones.get(areaId);
        if (existing == null) {
            return Optional.empty();
        }
        ShopZone updated = new ShopZone(existing.getAreaId(), existing.getAreaName(), existing.getCreatedBy(),
                existing.getCreatedAt(), ShopZone.normalizeMode(systemShop));
        zones.put(areaId, updated);
        save();
        return Optional.of(updated);
    }

    public synchronized boolean systemShopEnabledFor(Player player, boolean globalEnabled) {
        return zoneAt(player)
                .map(zone -> zone.systemShopEnabled(globalEnabled))
                .orElse(globalEnabled);
    }

    public synchronized boolean deleteAreaZone(long areaId) {
        boolean removed = zones.remove(areaId) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    private void save() {
        try {
            Files.createDirectories(zonesFile.getParent());
            Files.writeString(zonesFile, toJson(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Shop.logger().error("Could not save shop zones: " + ex.getMessage());
        }
    }

    private String toJson() {
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (ShopZone zone : listZones()) {
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("  {\n")
                    .append("    \"areaId\": ").append(zone.getAreaId()).append(",\n")
                    .append("    \"areaName\": \"").append(escape(zone.getAreaName())).append("\",\n")
                    .append("    \"createdBy\": \"").append(escape(zone.getCreatedBy())).append("\",\n")
                    .append("    \"createdAt\": ").append(zone.getCreatedAt()).append(",\n")
                    .append("    \"systemShop\": ").append(zone.getSystemShop()).append("\n")
                    .append("  }");
        }
        json.append("\n]\n");
        return json.toString();
    }

    private ShopZone fromJson(Map<String, Object> object) {
        return new ShopZone(
                longValue(object, "areaId", 0L),
                stringValue(object, "areaName"),
                stringValue(object, "createdBy"),
                longValue(object, "createdAt", 0L),
                (int) longValue(object, "systemShop", -1L));
    }

    private static String areaName(Area area) {
        return area.getName() == null || area.getName().isBlank() ? "Area #" + area.getID() : area.getName();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stringValue(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static long longValue(Map<String, Object> object, String key, long defaultValue) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static final class JsonObjects {
        private final String input;
        private int index;

        JsonObjects(String input) {
            this.input = input == null ? "" : input;
        }

        List<Map<String, Object>> parseArray() {
            skipWhitespace();
            if (index >= input.length()) {
                return List.of();
            }
            expect('[');
            List<Map<String, Object>> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(parseObject());
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                expect(']');
                return result;
            }
        }

        private Map<String, Object> parseObject() {
            skipWhitespace();
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                expect('}');
                return result;
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (peek('"')) {
                return parseString();
            }
            if (matches("true")) {
                index += 4;
                return Boolean.TRUE;
            }
            if (matches("false")) {
                index += 5;
                return Boolean.FALSE;
            }
            if (matches("null")) {
                index += 4;
                return null;
            }
            return parseNumber();
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char ch = input.charAt(index++);
                if (ch == '"') {
                    return result.toString();
                }
                if (ch == '\\') {
                    if (index >= input.length()) {
                        throw error("Unexpected end of escape sequence");
                    }
                    char escaped = input.charAt(index++);
                    result.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> escaped;
                    });
                } else {
                    result.append(ch);
                }
            }
            throw error("Unterminated string");
        }

        private Long parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (start == index || (input.charAt(start) == '-' && start + 1 == index)) {
                throw error("Expected number");
            }
            return Long.parseLong(input.substring(start, index));
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < input.length() && input.charAt(index) == expected;
        }

        private boolean matches(String value) {
            return input.regionMatches(index, value, 0, value.length());
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at index " + index);
        }
    }
}
