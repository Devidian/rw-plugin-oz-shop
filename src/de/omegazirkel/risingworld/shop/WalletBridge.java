package de.omegazirkel.risingworld.shop;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.risingworld.api.Plugin;

public class WalletBridge {
    private final Plugin owner;

    public WalletBridge(Plugin owner) {
        this.owner = owner;
    }

    public boolean isAvailable() {
        try {
            return owner.getPluginByName("OZ - Wallet") != null
                    && Class.forName("de.omegazirkel.risingworld.Wallet") != null;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public WalletCallResult withdrawDefault(int playerDbId, long value, String reason, String pluginIdentifier) {
        return call("withdrawDefault",
                new Class<?>[] { int.class, long.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, pluginIdentifier });
    }

    public WalletCallResult withdraw(int playerDbId, long value, String reason, String currencyIdentifier,
            String pluginIdentifier) {
        return call("withdraw",
                new Class<?>[] { int.class, long.class, String.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, currencyIdentifier, pluginIdentifier });
    }

    public WalletCallResult depositDefault(int playerDbId, long value, String reason, String pluginIdentifier) {
        return call("depositDefault",
                new Class<?>[] { int.class, long.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, pluginIdentifier });
    }

    public WalletCallResult deposit(int playerDbId, long value, String reason, String currencyIdentifier,
            String pluginIdentifier) {
        return call("deposit",
                new Class<?>[] { int.class, long.class, String.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, currencyIdentifier, pluginIdentifier });
    }

    public String defaultCurrencyIdentifier() {
        Plugin walletPlugin = owner.getPluginByName("OZ - Wallet");
        if (walletPlugin == null) {
            return "";
        }
        try {
            Method method = walletPlugin.getClass().getMethod("defaultCurrencyIdentifier");
            Object result = method.invoke(walletPlugin);
            return result instanceof String ? (String) result : "";
        } catch (ReflectiveOperationException ex) {
            return "";
        }
    }

    public List<CurrencyInfo> listCurrencies() {
        Object result = callRaw("listCurrencies", new Class<?>[] {}, new Object[] {});
        if (!WalletCallResult.from(result).success()) {
            return List.of();
        }
        Object currencies = field(result, "currencies");
        if (!(currencies instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<CurrencyInfo> infos = new ArrayList<>();
        for (Object currency : iterable) {
            CurrencyInfo info = CurrencyInfo.from(currency);
            if (info != null) {
                infos.add(info);
            }
        }
        return List.copyOf(infos);
    }

    public BalanceInfo balance(int playerDbId, String currencyIdentifier) {
        Object result = callRaw("balance",
                new Class<?>[] { int.class, String.class },
                new Object[] { playerDbId, currencyIdentifier });
        if (!WalletCallResult.from(result).success()) {
            return new BalanceInfo(false, 0L);
        }
        Object balance = field(result, "balance");
        Object value = callGetter(balance, "getBalance");
        return value instanceof Long ? new BalanceInfo(true, (Long) value) : new BalanceInfo(false, 0L);
    }

    private WalletCallResult call(String methodName, Class<?>[] paramTypes, Object[] args) {
        WalletCallResult result = WalletCallResult.from(callRaw(methodName, paramTypes, args));
        if (!result.success() && result.message().isBlank()) {
            return new WalletCallResult(false, "OZ - Wallet is not installed, not loaded, or did not answer.");
        }
        return result;
    }

    private Object callRaw(String methodName, Class<?>[] paramTypes, Object[] args) {
        Plugin walletPlugin = owner.getPluginByName("OZ - Wallet");
        if (walletPlugin == null) {
            return null;
        }

        try {
            Method method = walletPlugin.getClass().getMethod(methodName, paramTypes);
            return method.invoke(walletPlugin, args);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Object field(Object result, String fieldName) {
        if (result == null) {
            return null;
        }
        try {
            Field field = result.getClass().getField(fieldName);
            return field.get(result);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public record WalletCallResult(boolean success, String message) {
        static WalletCallResult from(Object result) {
            Object success = fieldStatic(result, "success");
            Object message = fieldStatic(result, "message");
            return new WalletCallResult(Boolean.TRUE.equals(success), message instanceof String ? (String) message : "");
        }

        public static WalletCallResult success(String message) {
            return new WalletCallResult(true, message);
        }
    }

    public record CurrencyInfo(
            String identifier,
            String name,
            String iconKey,
            String pluginIdentifier,
            boolean defaultCurrency) {
        static CurrencyInfo from(Object currency) {
            Object identifier = callGetter(currency, "getIdentifier");
            if (!(identifier instanceof String text) || text.isBlank()) {
                return null;
            }
            Object name = callGetter(currency, "getName");
            Object iconKey = callGetter(currency, "getIconKey");
            Object pluginIdentifier = callGetter(currency, "getPluginIdentifier");
            Object defaultCurrency = callGetter(currency, "isDefaultCurrency");
            return new CurrencyInfo(
                    text.trim().toUpperCase(Locale.ROOT),
                    name instanceof String ? (String) name : "",
                    iconKey instanceof String ? (String) iconKey : "",
                    pluginIdentifier instanceof String ? (String) pluginIdentifier : "",
                    defaultCurrency instanceof Boolean && (Boolean) defaultCurrency);
        }
    }

    public record BalanceInfo(boolean success, long balance) {
    }

    private static Object callGetter(Object target, String getter) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(getter);
            return method.invoke(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static Object fieldStatic(Object result, String fieldName) {
        if (result == null) {
            return null;
        }
        try {
            Field field = result.getClass().getField(fieldName);
            return field.get(result);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
