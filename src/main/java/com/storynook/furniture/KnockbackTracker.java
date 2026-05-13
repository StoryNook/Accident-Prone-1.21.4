package com.storynook.furniture;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * Per-player "last knockback tick" map. The soft-containment task
 * reads this to release wards whose recent damage was the kind that
 * physically launches them.
 *
 * <p>Note: DamageCause.WIND_CHARGE is not present in the Spigot 1.21.4
 * API jar used by this project. Wind charges produce PROJECTILE damage
 * and are therefore already covered by that cause.
 */
public final class KnockbackTracker {

    private static final Set<DamageCause> KNOCKBACK_CAUSES = EnumSet.of(
        DamageCause.ENTITY_ATTACK,
        DamageCause.ENTITY_SWEEP_ATTACK,
        DamageCause.PROJECTILE,
        DamageCause.BLOCK_EXPLOSION,
        DamageCause.ENTITY_EXPLOSION,
        DamageCause.THORNS
    );

    private final Map<UUID, Long> lastDamage = new ConcurrentHashMap<>();

    public static boolean isKnockbackCause(DamageCause cause) {
        return KNOCKBACK_CAUSES.contains(cause);
    }

    public void stamp(UUID playerUuid, long tick) {
        lastDamage.put(playerUuid, tick);
    }

    public long lastDamageTick(UUID playerUuid) {
        return lastDamage.getOrDefault(playerUuid, Long.MIN_VALUE);
    }

    /** Removes entries older than {@code currentTick - maxAgeTicks}. */
    public void prune(long currentTick, long maxAgeTicks) {
        long cutoff = currentTick - maxAgeTicks;
        lastDamage.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
