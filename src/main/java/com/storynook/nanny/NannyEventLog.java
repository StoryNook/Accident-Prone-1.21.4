package com.storynook.nanny;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.storynook.Plugin;

/**
 * NannyEventLog maintains an in-memory rolling log of events for a nanny
 * and periodically persists them to YAML.
 *
 * Phase 4 will add full AI context serialization. Phase 1 provides the
 * skeleton so other classes can call log() without compile errors.
 */
public class NannyEventLog {

    // -------------------------------------------------------------------------
    // NannyEventType enum
    // -------------------------------------------------------------------------

    public enum NannyEventType {
        CHANGED_WARD,
        FED_WARD,
        EQUIPPED_WARD,
        WARD_HAD_ACCIDENT,
        LOCKED_WARD,
        PLACED_IN_CRIB,
        FORCE_FED,
        LEASHED_WARD,
        HYPNOTIZED_WARD,
        LOW_SUPPLIES,
        RETURNED_HOME,
        FOUND_WARD,
        NANNY_CHAT,
        WARD_CHAT,
        SEEKING_WARD,
        STUCK_TELEPORT,
        DIAPER_PUNISHMENT_STARTED,
        DIAPER_PUNISHMENT_VIOLATED,
        DIAPER_PUNISHMENT_ESCALATED,
        DIAPER_PUNISHMENT_EXPIRED,
        BEHAVIOR_SCORE_CHANGED
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final NannyData nannyData;
    private final Plugin plugin;
    private final List<String> eventLog;
    private static final int MAX_EVENTS = 100;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public NannyEventLog(NannyData nannyData, Plugin plugin) {
        this.nannyData = nannyData;
        this.plugin = plugin;
        this.eventLog = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // log() method
    // -------------------------------------------------------------------------

    /**
     * Log an event to the in-memory list and schedule async YAML write.
     *
     * Format: "[yyyy-MM-dd HH:mm:ss] TYPE | ward:UUID | details"
     * wardUUID may be null — uses "null" string in that case.
     *
     * @param type      The event type
     * @param wardUUID  The UUID of the affected ward (may be null)
     * @param details   Additional details
     */
    public void log(NannyEventType type, UUID wardUUID, String details) {
        // Build the log entry
        String timestamp = LocalDateTime.now().format(DATE_FMT);
        String wardStr = (wardUUID != null) ? wardUUID.toString() : "null";
        String entry = String.format("[%s] %s | ward:%s | %s",
                timestamp, type.name(), wardStr, details);

        // Add to in-memory list
        eventLog.add(entry);

        // Trim to MAX_EVENTS if necessary
        if (eventLog.size() > MAX_EVENTS) {
            eventLog.remove(0);
        }

        // Schedule async YAML write
        scheduleAsyncSave();
    }

    // -------------------------------------------------------------------------
    // Async YAML save
    // -------------------------------------------------------------------------

    private void scheduleAsyncSave() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                saveEventLog();
            }
        });
    }

    private void saveEventLog() {
        File nanniesDir = new File(plugin.getDataFolder(), "nannies");
        if (!nanniesDir.exists()) {
            nanniesDir.mkdirs();
        }

        File nannyFile = new File(nanniesDir, nannyData.getNannyUUID().toString() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(nannyFile);

        // Set the event log
        config.set("eventLog", eventLog);

        try {
            config.save(nannyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // loadFromData() static factory
    // -------------------------------------------------------------------------

    /**
     * Create a new NannyEventLog and load existing entries from the
     * nannies/{nannyUUID}.yml file's eventLog: key (if it exists).
     *
     * @param nannyData The nanny data object
     * @param plugin    The plugin instance
     * @return A new NannyEventLog with loaded entries
     */
    public static NannyEventLog loadFromData(NannyData nannyData, Plugin plugin) {
        NannyEventLog log = new NannyEventLog(nannyData, plugin);

        File nanniesDir = new File(plugin.getDataFolder(), "nannies");
        File nannyFile = new File(nanniesDir, nannyData.getNannyUUID().toString() + ".yml");

        if (nannyFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(nannyFile);
            List<String> loadedEntries = config.getStringList("eventLog");
            if (loadedEntries != null && !loadedEntries.isEmpty()) {
                log.eventLog.addAll(loadedEntries);
            }
        }

        return log;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Get the current in-memory event log.
     *
     * @return The list of log entries
     */
    public List<String> getEventLog() {
        return eventLog;
    }
}
