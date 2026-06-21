package dev.li2fox.vibepetcore.pet;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class WorldGuardCombatBridge {
    private static final long CACHE_MILLIS = 1_500L;

    private record CachedDecision(String world, int x, int y, int z, boolean blocked, long expiresAt) {
    }

    private final Plugin worldGuardPlugin;
    private final Method wrapPlayerMethod;
    private final Method adaptLocationMethod;
    private final Method worldGuardGetInstanceMethod;
    private final Method getPlatformMethod;
    private final Method getRegionContainerMethod;
    private final Method createQueryMethod;
    private final Method testStateMethod;
    private final Object pvpFlag;
    private final Map<UUID, CachedDecision> cache = new ConcurrentHashMap<>();

    private WorldGuardCombatBridge(
        Plugin worldGuardPlugin,
        Method wrapPlayerMethod,
        Method adaptLocationMethod,
        Method worldGuardGetInstanceMethod,
        Method getPlatformMethod,
        Method getRegionContainerMethod,
        Method createQueryMethod,
        Method testStateMethod,
        Object pvpFlag
    ) {
        this.worldGuardPlugin = worldGuardPlugin;
        this.wrapPlayerMethod = wrapPlayerMethod;
        this.adaptLocationMethod = adaptLocationMethod;
        this.worldGuardGetInstanceMethod = worldGuardGetInstanceMethod;
        this.getPlatformMethod = getPlatformMethod;
        this.getRegionContainerMethod = getRegionContainerMethod;
        this.createQueryMethod = createQueryMethod;
        this.testStateMethod = testStateMethod;
        this.pvpFlag = pvpFlag;
    }

    static WorldGuardCombatBridge create() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (plugin == null) {
            return null;
        }
        try {
            Method wrapPlayer = plugin.getClass().getMethod("wrapPlayer", Player.class);
            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptLocation = bukkitAdapterClass.getMethod("adapt", Location.class);
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstance = worldGuardClass.getMethod("getInstance");
            Object worldGuard = getInstance.invoke(null);
            Method getPlatform = worldGuard.getClass().getMethod("getPlatform");
            Object platform = getPlatform.invoke(worldGuard);
            Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
            Object regionContainer = getRegionContainer.invoke(platform);
            Method createQuery = regionContainer.getClass().getMethod("createQuery");
            Object query = createQuery.invoke(regionContainer);
            Method testState = null;
            for (Method method : query.getClass().getMethods()) {
                if (method.getName().equals("testState") && method.getParameterCount() == 3) {
                    testState = method;
                    break;
                }
            }
            if (testState == null) {
                return null;
            }
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object pvpFlag = flagsClass.getField("PVP").get(null);
            return new WorldGuardCombatBridge(plugin, wrapPlayer, adaptLocation, getInstance, getPlatform, getRegionContainer, createQuery, testState, pvpFlag);
        } catch (Throwable ignored) {
            return null;
        }
    }

    boolean blocksCombat(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        Location location = player.getLocation();
        long now = System.currentTimeMillis();
        CachedDecision cached = cache.get(player.getUniqueId());
        if (cached != null
            && cached.expiresAt() > now
            && cached.world().equals(location.getWorld().getName())
            && cached.x() == location.getBlockX()
            && cached.y() == location.getBlockY()
            && cached.z() == location.getBlockZ()) {
            return cached.blocked();
        }
        boolean blocked = queryBlocked(player, location);
        cache.put(player.getUniqueId(), new CachedDecision(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            blocked,
            now + CACHE_MILLIS
        ));
        return blocked;
    }

    private boolean queryBlocked(Player player, Location location) {
        try {
            Object localPlayer = wrapPlayerMethod.invoke(worldGuardPlugin, player);
            Object adaptedLocation = adaptLocationMethod.invoke(null, location);
            Object worldGuard = worldGuardGetInstanceMethod.invoke(null);
            Object platform = getPlatformMethod.invoke(worldGuard);
            Object regionContainer = getRegionContainerMethod.invoke(platform);
            Object query = createQueryMethod.invoke(regionContainer);
            Object flagsArray = Array.newInstance(pvpFlag.getClass(), 1);
            Array.set(flagsArray, 0, pvpFlag);
            Object result = testStateMethod.invoke(query, adaptedLocation, localPlayer, flagsArray);
            return result instanceof Boolean allowed && !allowed;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
