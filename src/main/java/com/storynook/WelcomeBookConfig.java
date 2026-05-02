package com.storynook;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Parses welcomebook.yml from the plugin data folder into an ordered list of
 * Section objects. No rendering or Bukkit-item logic lives here.
 */
public final class WelcomeBookConfig {

    public static final class Section {
        public final String id;
        public final String heading;
        public final String body;
        public final List<String> requires; // empty = always shown

        public Section(String id, String heading, String body, List<String> requires) {
            this.id = id;
            this.heading = heading;
            this.body = body;
            this.requires = requires;
        }
    }

    private WelcomeBookConfig() {}

    /**
     * Reads <dataFolder>/welcomebook.yml and returns sections in the order
     * specified by the file's `order:` list. Sections referenced in `order:`
     * but missing from `sections:` are skipped with a warning. Sections in
     * `sections:` but not in `order:` are silently dropped.
     *
     * Returns an empty list (and logs a severe warning) if the file is missing
     * or malformed; callers should render a fallback page in that case.
     */
    public static List<Section> load(Plugin plugin) {
        Logger log = plugin.getLogger();
        File file = new File(plugin.getDataFolder(), "welcomebook.yml");

        if (!file.exists()) {
            log.severe("welcomebook.yml is missing from the data folder.");
            return Collections.emptyList();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> order = yaml.getStringList("order");
        ConfigurationSection sections = yaml.getConfigurationSection("sections");

        if (order.isEmpty() || sections == null) {
            log.severe("welcomebook.yml is malformed: missing 'order' or 'sections'.");
            return Collections.emptyList();
        }

        List<Section> result = new ArrayList<Section>();
        for (String id : order) {
            ConfigurationSection node = sections.getConfigurationSection(id);
            if (node == null) {
                log.warning("welcomebook.yml: section '" + id + "' is in 'order' but not in 'sections'. Skipping.");
                continue;
            }

            String heading = node.getString("heading", "");
            String body = node.getString("body", "");

            if (heading.isEmpty()) {
                log.warning("welcomebook.yml: section '" + id + "' has no heading. Using id as fallback.");
                heading = id;
            }

            List<String> requires = readRequires(node);
            result.add(new Section(id, heading, body, requires));
        }

        return result;
    }

    /**
     * `requires:` may be absent, a single string, or a list of strings. Always
     * normalize to a list (empty = always shown).
     */
    private static List<String> readRequires(ConfigurationSection node) {
        if (!node.contains("requires")) {
            return Collections.emptyList();
        }
        Object raw = node.get("requires");
        if (raw instanceof String) {
            return Collections.singletonList((String) raw);
        }
        if (raw instanceof List) {
            List<String> out = new ArrayList<String>();
            for (Object item : (List<?>) raw) {
                if (item != null) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
