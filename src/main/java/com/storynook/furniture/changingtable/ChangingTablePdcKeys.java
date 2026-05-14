package com.storynook.furniture.changingtable;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Centralised PDC keys for changing tables. All keys are namespaced under the
 * plugin so they cannot collide with other plugins' PDC.
 */
public final class ChangingTablePdcKeys {

    public final NamespacedKey id;
    public final NamespacedKey colorIndex;
    public final NamespacedKey yaw;
    public final NamespacedKey anchorX;
    public final NamespacedKey anchorY;
    public final NamespacedKey anchorZ;
    public final NamespacedKey displayUuid;
    public final NamespacedKey ownerUuid;

    public static final String SCOREBOARD_TAG = "accidentprone.changing_table";

    public ChangingTablePdcKeys(Plugin plugin) {
        this.id          = new NamespacedKey(plugin, "ct_id");
        this.colorIndex  = new NamespacedKey(plugin, "ct_color");
        this.yaw         = new NamespacedKey(plugin, "ct_yaw");
        this.anchorX     = new NamespacedKey(plugin, "ct_anchor_x");
        this.anchorY     = new NamespacedKey(plugin, "ct_anchor_y");
        this.anchorZ     = new NamespacedKey(plugin, "ct_anchor_z");
        this.displayUuid = new NamespacedKey(plugin, "ct_display_uuid");
        this.ownerUuid   = new NamespacedKey(plugin, "ct_owner_uuid");
    }
}
