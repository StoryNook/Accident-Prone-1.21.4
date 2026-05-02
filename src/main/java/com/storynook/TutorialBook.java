package com.storynook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class TutorialBook {

    private static final int PAGE_CHARACTER_LIMIT = 256;

    private final Plugin plugin;

    public TutorialBook(Plugin plugin) {
        this.plugin = plugin;
    }

    public void giveWelcomeBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(ChatColor.GOLD + "Welcome to Accident Prone!");
        meta.setAuthor("Storynook");

        List<String> pages = new ArrayList<String>();
        List<WelcomeBookConfig.Section> sections = WelcomeBookConfig.load(plugin);

        if (sections.isEmpty()) {
            pages.add(ChatColor.BOLD + "Welcome Book\n\n" + ChatColor.RESET
                    + "The welcome book is misconfigured. Please contact your server admin.");
        } else {
            Map<String, Object> globalConfig = plugin.getGlobalConfig();
            for (WelcomeBookConfig.Section section : sections) {
                if (!flagsEnabled(section.requires, globalConfig)) {
                    continue;
                }
                addSection(pages, section.heading, section.body);
            }
        }

        addSection(pages, "Credits", buildCreditsBody());

        meta.setPages(pages);
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
    }

    /**
     * Hardcoded contributor credits. Lives in code (not welcomebook.yml) so it
     * can't be edited away by an admin. Update this list directly when adding
     * new contributors.
     */
    private String buildCreditsBody() {
        StringBuilder b = new StringBuilder();
        b.append("Thanks to everyone who contributed art, sound, and ideas to this plugin:\n\n");
        b.append("Sonodork - tummy rumble sounds\n");
        b.append("Flarantus - crib\n");
        b.append("lilbabjojo - pacifiers, candy\n");
        b.append("Sammydini020 - diaper pail\n");
        b.append("PieceOfSoap - base diaper side images\n");
        b.append("The Editor - custom icons, custom diaper side images\n");
        b.append("Kitters / DitzyDayCare - temporary use of textures\n");
        return b.toString();
    }

    /**
     * Renders one section: bolded heading on its own line, blank line, then the
     * body word-wrapped into pages of <= PAGE_CHARACTER_LIMIT characters.
     */
    private void addSection(List<String> pages, String heading, String body) {
        StringBuilder content = new StringBuilder();
        content.append(ChatColor.BOLD).append(heading).append(ChatColor.RESET).append("\n\n");
        content.append(body);
        addPagesWithText(pages, content);
    }

    private void addPagesWithText(List<String> pages, StringBuilder sectionContent) {
        String content = sectionContent.toString();
        String[] words = content.split(" ");
        StringBuilder currentPage = new StringBuilder();

        for (String word : words) {
            if (currentPage.length() + word.length() + 1 > PAGE_CHARACTER_LIMIT) {
                pages.add(currentPage.toString());
                currentPage = new StringBuilder(word);
            } else {
                if (currentPage.length() > 0) currentPage.append(" ");
                currentPage.append(word);
            }
        }

        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }
    }

    /**
     * Evaluate a section's `requires` list against the plugin's flattened
     * globalConfig. Empty list = always true. Unknown flags log a warning and
     * are treated as false (catches typos like the existing Hyponsis/Hypnosis
     * mismatch, so the section stays hidden rather than silently visible).
     */
    private boolean flagsEnabled(List<String> requires, Map<String, Object> globalConfig) {
        if (requires == null || requires.isEmpty()) {
            return true;
        }
        Logger log = plugin.getLogger();
        for (String flag : requires) {
            if (globalConfig == null || !globalConfig.containsKey(flag)) {
                log.warning("welcomebook.yml: section requires unknown flag '" + flag + "'. Hiding section.");
                return false;
            }
            Object value = globalConfig.get(flag);
            if (!(value instanceof Boolean) || !((Boolean) value).booleanValue()) {
                return false;
            }
        }
        return true;
    }
}
