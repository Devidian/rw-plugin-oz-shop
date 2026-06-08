package de.omegazirkel.risingworld.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private final Connection connection;
    private final Map<Long, ShopZone> zones = new LinkedHashMap<>();

    public ShopZoneService(Shop plugin, Connection connection, String configuredFileName) {
        this.connection = connection;
        Path pluginPath = Paths.get(plugin.getPath() != null ? plugin.getPath() : ".");
        this.zonesFile = pluginPath.resolve(configuredFileName == null || configuredFileName.isBlank()
                ? "shop-zones.json"
                : configuredFileName);
        initialize();
        load();
    }

    public synchronized void load() {
        zones.clear();
        try {
            importJsonIfDatabaseEmpty();
            loadFromDatabase();
        } catch (IOException | SQLException | IllegalArgumentException ex) {
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
                        existing.getSystemShop(), existing.getSystemOffersFile());
        zones.put(area.getID(), zone);
        save(zone);
        return Optional.of(zone);
    }

    public synchronized Optional<ShopZone> setSystemShopMode(long areaId, int systemShop) {
        ShopZone existing = zones.get(areaId);
        if (existing == null) {
            return Optional.empty();
        }
        ShopZone updated = new ShopZone(existing.getAreaId(), existing.getAreaName(), existing.getCreatedBy(),
                existing.getCreatedAt(), ShopZone.normalizeMode(systemShop), existing.getSystemOffersFile());
        zones.put(areaId, updated);
        save(updated);
        return Optional.of(updated);
    }

    public synchronized Optional<ShopZone> setZoneName(long areaId, String name) {
        ShopZone existing = zones.get(areaId);
        if (existing == null) {
            return Optional.empty();
        }
        String zoneName = name == null || name.trim().isBlank() ? "Area #" + areaId : name.trim();
        ShopZone updated = new ShopZone(existing.getAreaId(), zoneName, existing.getCreatedBy(),
                existing.getCreatedAt(), existing.getSystemShop(), existing.getSystemOffersFile());
        zones.put(areaId, updated);
        save(updated);
        return Optional.of(updated);
    }

    public synchronized Optional<ShopZone> syncCurrentZoneName(Player player) {
        Area area = player == null ? null : player.getCurrentArea();
        if (area == null || area.getID() <= 0L) {
            return Optional.empty();
        }
        return setZoneName(area.getID(), areaName(area));
    }

    public synchronized Optional<ShopZone> setSystemOffersFile(long areaId, String systemOffersFile) {
        ShopZone existing = zones.get(areaId);
        if (existing == null) {
            return Optional.empty();
        }
        ShopZone updated = new ShopZone(existing.getAreaId(), existing.getAreaName(), existing.getCreatedBy(),
                existing.getCreatedAt(), existing.getSystemShop(), systemOffersFile);
        zones.put(areaId, updated);
        save(updated);
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
            delete(areaId);
        }
        return removed;
    }

    private void initialize() {
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_zones (
                        area_id BIGINT PRIMARY KEY,
                        area_name TEXT NOT NULL,
                        created_by TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        system_shop INTEGER NOT NULL DEFAULT -1,
                        system_offers_file TEXT NOT NULL DEFAULT ''
                    );
                    """);
            if (!columnExists("shop_zones", "system_offers_file")) {
                statement.execute("""
                        ALTER TABLE shop_zones
                        ADD COLUMN system_offers_file TEXT NOT NULL DEFAULT '';
                        """);
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not initialize shop-zone database: " + ex.getMessage());
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ");");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void loadFromDatabase() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT area_id, area_name, created_by, created_at, system_shop, system_offers_file
                FROM shop_zones
                ORDER BY area_name COLLATE NOCASE ASC, area_id ASC;
                """);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                ShopZone zone = new ShopZone(
                        result.getLong("area_id"),
                        result.getString("area_name"),
                        result.getString("created_by"),
                        result.getLong("created_at"),
                        result.getInt("system_shop"),
                        result.getString("system_offers_file"));
                if (zone.getAreaId() > 0) {
                    zones.put(zone.getAreaId(), zone);
                }
            }
        }
    }

    private void importJsonIfDatabaseEmpty() throws IOException, SQLException {
        if (Files.notExists(zonesFile) || !databaseEmpty()) {
            return;
        }
        String json = Files.readString(zonesFile, StandardCharsets.UTF_8);
        for (Map<String, Object> object : new JsonObjects(json).parseArray()) {
            ShopZone zone = fromJson(object);
            if (zone.getAreaId() > 0) {
                save(zone);
            }
        }
    }

    private boolean databaseEmpty() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS count FROM shop_zones;");
                ResultSet result = statement.executeQuery()) {
            return !result.next() || result.getInt("count") == 0;
        }
    }

    private void save(ShopZone zone) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shop_zones(area_id, area_name, created_by, created_at, system_shop, system_offers_file)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(area_id) DO UPDATE SET
                    area_name=excluded.area_name,
                    created_by=excluded.created_by,
                    created_at=excluded.created_at,
                    system_shop=excluded.system_shop,
                    system_offers_file=excluded.system_offers_file;
                """)) {
            statement.setLong(1, zone.getAreaId());
            statement.setString(2, zone.getAreaName());
            statement.setString(3, zone.getCreatedBy());
            statement.setLong(4, zone.getCreatedAt());
            statement.setInt(5, zone.getSystemShop());
            statement.setString(6, zone.getSystemOffersFile());
            statement.executeUpdate();
        } catch (SQLException ex) {
            Shop.logger().error("Could not save shop zones: " + ex.getMessage());
        }
    }

    private void delete(long areaId) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM shop_zones WHERE area_id = ?;")) {
            statement.setLong(1, areaId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            Shop.logger().error("Could not delete shop zone: " + ex.getMessage());
        }
    }

    private ShopZone fromJson(Map<String, Object> object) {
        return new ShopZone(
                longValue(object, "areaId", 0L),
                stringValue(object, "areaName"),
                stringValue(object, "createdBy"),
                longValue(object, "createdAt", 0L),
                (int) longValue(object, "systemShop", -1L),
                stringValue(object, "systemOffersFile"));
    }

    private static String areaName(Area area) {
        return area.getName() == null || area.getName().isBlank() ? "Area #" + area.getID() : area.getName();
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
