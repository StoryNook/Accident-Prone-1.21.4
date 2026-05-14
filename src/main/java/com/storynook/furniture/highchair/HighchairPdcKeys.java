package com.storynook.furniture.highchair;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Centralised PDC keys for highchairs. All keys are namespaced under the
 * plugin so they cannot collide with other plugins' PDC.
 */
public final class HighchairPdcKeys {

    public final NamespacedKey id;
    public final NamespacedKey colorIndex;
    public final NamespacedKey yaw;
    public final NamespacedKey displayUuid;
    public final NamespacedKey ownerUuid;
    public final NamespacedKey originX;
    public final NamespacedKey originY;
    public final NamespacedKey originZ;

    public static final String SCOREBOARD_TAG = "accidentprone.highchair";

    public HighchairPdcKeys(Plugin plugin) {
        this.id          = new NamespacedKey(plugin, "highchair_id");
        this.colorIndex  = new NamespacedKey(plugin, "highchair_color_index");
        this.yaw         = new NamespacedKey(plugin, "highchair_yaw");
        this.displayUuid = new NamespacedKey(plugin, "highchair_display_uuid");
        this.ownerUuid   = new NamespacedKey(plugin, "highchair_owner_uuid");
        this.originX     = new NamespacedKey(plugin, "highchair_origin_x");
        this.originY     = new NamespacedKey(plugin, "highchair_origin_y");
        this.originZ     = new NamespacedKey(plugin, "highchair_origin_z");
    }
}
