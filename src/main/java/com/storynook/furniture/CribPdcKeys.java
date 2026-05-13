package com.storynook.furniture;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Centralised PDC keys for new-system cribs. All keys are namespaced
 * under the plugin so they cannot collide with other plugins' PDC.
 */
public final class CribPdcKeys {

    public final NamespacedKey id;
    public final NamespacedKey woodVariant;
    public final NamespacedKey yaw;
    public final NamespacedKey displayUuid;
    public final NamespacedKey ownerUuid;
    public final NamespacedKey floorX;
    public final NamespacedKey floorY;
    public final NamespacedKey floorZ;
    public final NamespacedKey bedHeadX;
    public final NamespacedKey bedHeadY;
    public final NamespacedKey bedHeadZ;

    public static final String SCOREBOARD_TAG = "accidentprone.crib";

    public CribPdcKeys(Plugin plugin) {
        this.id          = new NamespacedKey(plugin, "crib_id");
        this.woodVariant = new NamespacedKey(plugin, "crib_wood_variant");
        this.yaw         = new NamespacedKey(plugin, "crib_yaw");
        this.displayUuid = new NamespacedKey(plugin, "crib_display_uuid");
        this.ownerUuid   = new NamespacedKey(plugin, "crib_owner_uuid");
        this.floorX      = new NamespacedKey(plugin, "crib_floor_x");
        this.floorY      = new NamespacedKey(plugin, "crib_floor_y");
        this.floorZ      = new NamespacedKey(plugin, "crib_floor_z");
        this.bedHeadX    = new NamespacedKey(plugin, "crib_bed_head_x");
        this.bedHeadY    = new NamespacedKey(plugin, "crib_bed_head_y");
        this.bedHeadZ    = new NamespacedKey(plugin, "crib_bed_head_z");
    }
}
