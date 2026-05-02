package com.storynook.nanny;

import java.util.HashMap;
import java.util.Map;

/**
 * Static gate for "is this Nanny allowed to do X?". All gameplay code that
 * cares about mood-tier permissions calls
 * {@link #allows(NannyData, Capability)}.
 *
 * <p>Mood tier rules:
 * <ul>
 *   <li>SWEET — basic care only.</li>
 *   <li>CARING — basic care + potty reminders.</li>
 *   <li>STRICT — CARING + armor lock + crib + caregiver block (default ON).</li>
 *   <li>WARDEN — STRICT + every Phase 5b capability.</li>
 *   <li>CUSTOM — read from {@code data.customSettings}; missing key = false.</li>
 * </ul>
 */
public final class NannyPolicy {

    private static final Map<String, java.util.Set<Capability>> TIER_DEFAULTS = buildDefaults();

    private NannyPolicy() {}

    public static boolean allows(NannyData data, Capability cap) {
        if (data == null || cap == null) return false;
        if (cap == Capability.BASIC_CARE) return true;
        NannyData.MoodTier tier = data.getMoodTier() != null
                ? data.getMoodTier() : NannyData.MoodTier.CARING;

        if (tier == NannyData.MoodTier.CUSTOM) {
            Boolean v = data.getCustomSettings().get(cap.name());
            return v != null && v;
        }

        if (cap == Capability.BLOCK_CAREGIVERS) {
            if (tier == NannyData.MoodTier.STRICT || tier == NannyData.MoodTier.WARDEN) {
                return data.isBlockOtherCaregivers();
            }
            return false;
        }

        java.util.Set<Capability> allowed = TIER_DEFAULTS.get(tier.name());
        return allowed != null && allowed.contains(cap);
    }

    public static NannyData.MoodTier minTier(Capability cap) {
        switch (cap) {
            case BASIC_CARE:
                return NannyData.MoodTier.SWEET;
            case POTTY_REMINDERS:
                return NannyData.MoodTier.CARING;
            case ARMOR_LOCK:
            case CRIB_PLACEMENT:
            case BLOCK_CAREGIVERS:
                return NannyData.MoodTier.STRICT;
            default:
                return NannyData.MoodTier.WARDEN;
        }
    }

    private static Map<String, java.util.Set<Capability>> buildDefaults() {
        Map<String, java.util.Set<Capability>> m = new HashMap<>();
        m.put("SWEET", set(Capability.BASIC_CARE));
        m.put("CARING", set(Capability.BASIC_CARE, Capability.POTTY_REMINDERS));
        m.put("STRICT", set(Capability.BASIC_CARE, Capability.POTTY_REMINDERS,
                Capability.ARMOR_LOCK, Capability.CRIB_PLACEMENT));
        m.put("WARDEN", set(Capability.BASIC_CARE, Capability.POTTY_REMINDERS,
                Capability.ARMOR_LOCK, Capability.CRIB_PLACEMENT,
                Capability.FORCE_FEED_LAXATIVE, Capability.BINDING_LEGGINGS,
                Capability.LEASH_WARD, Capability.HYPNOSIS_USE,
                Capability.ROOM_LOCKDOWN, Capability.EVIL_CRAFTING));
        return m;
    }

    private static java.util.Set<Capability> set(Capability... caps) {
        java.util.Set<Capability> s = new java.util.HashSet<>();
        for (Capability c : caps) s.add(c);
        return s;
    }
}
