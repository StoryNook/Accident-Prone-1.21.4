package com.storynook;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.Integrations.PlaceholderAPIHook;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Bukkit;

public class ScoreBoard {

    private static ScoreboardManager manager = Bukkit.getScoreboardManager();

    /** Logged once per session when the placeholder fails to resolve. Reset by /diaperreload. */
    public static boolean balanceLineWarned = false;

    private static String tryBuildBalanceLine(Plugin plugin, Player player) {
        Object placeholderObj = plugin.getGlobalConfig().get("Balance_Placeholder");
        if (!(placeholderObj instanceof String)) {
            return null;
        }
        String placeholder = (String) placeholderObj;
        if (placeholder.isEmpty()) {
            return null;
        }

        String resolved;
        try {
            resolved = PlaceholderAPIHook.setPlaceholders(player, placeholder);
        } catch (Throwable t) {
            if (!balanceLineWarned) {
                plugin.getLogger().info("Balance display: PlaceholderAPI threw resolving '"
                        + placeholder + "': " + t.getMessage()
                        + ". Suppressing further warnings until /diaperreload.");
                balanceLineWarned = true;
            }
            return null;
        }

        if (resolved == null || resolved.isEmpty() || resolved.equals(placeholder)) {
            if (!balanceLineWarned) {
                plugin.getLogger().info("Balance display: placeholder '" + placeholder
                        + "' did not resolve (got '" + resolved + "'). Is the right expansion installed?"
                        + " Suppressing further warnings until /diaperreload.");
                balanceLineWarned = true;
            }
            return null;
        }

        // Some economy expansions return formatted strings like "1,234.56" or "$1,234.56".
        // Strip non-numeric characters except '.', '-' before parsing.
        String cleaned = resolved.replaceAll("[^0-9.\\-]", "");
        double amount;
        try {
            amount = Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            if (!balanceLineWarned) {
                plugin.getLogger().info("Balance display: placeholder '" + placeholder
                        + "' resolved to '" + resolved + "' which doesn't parse as a number."
                        + " Suppressing further warnings until /diaperreload.");
                balanceLineWarned = true;
            }
            return null;
        }

        return ChatColor.GREEN + "$ " + ChatColor.GOLD + BalanceFormatter.format(amount);
    }

    private static String getUnderwearStateString(int wetness, int fullness) {
        int barLength = 40;
        double wetBars = Math.min(wetness/2.5, 40);
        double messBars = Math.min(fullness/2.5, 50);

        int filledBars = (int) Math.max(wetBars, Math.min(messBars, 50));
        int lowerBars = (int) Math.min(wetBars, Math.min(messBars, 50));

        StringBuilder UnderwearState = new StringBuilder();
        if (wetBars > messBars) {
            for (int i = 0; i < lowerBars; i++) {
                UnderwearState.append(ChatColor.GREEN);
                UnderwearState.append("|");
                    
            }
            for (int i = lowerBars; i < filledBars; i++) {
                UnderwearState.append(ChatColor.YELLOW);
                UnderwearState.append("|");
                    
            }
        }
        else if (messBars > wetBars) {
            for (int i = 0; i < lowerBars; i++) {
                    UnderwearState.append(ChatColor.YELLOW);
                    UnderwearState.append("|");
                }
                for (int i = lowerBars; i < filledBars; i++) {
                    UnderwearState.append(ChatColor.GREEN);
                    UnderwearState.append("|");
                }
        }
        else if (messBars == wetBars) {
            for (int i = 0; i < filledBars; i++) {
                UnderwearState.append(ChatColor.GREEN);
                UnderwearState.append("|");
            }
        }

        for (int i = filledBars; i < barLength; i++) {
            UnderwearState.append(ChatColor.RESET);
            UnderwearState.append("|");
        }

        return UnderwearState.toString();
    }

    private static String getBladderBarString(int bladderLevel) {
        int barLength = 10;
        int filledBars = bladderLevel/10;

        StringBuilder bladderBar = new StringBuilder();
        // bladderBar.append(color);  // Use the passed color for the filled part
        for (int i = 0; i < filledBars; i++) {
            bladderBar.append("\uE050");
        }

        // bladderBar.append(ChatColor.RESET);  // Reset color for the empty part
        for (int i = filledBars; i < barLength; i++) {
            bladderBar.append("\uE051");
        }

        return bladderBar.toString();
    }
    private static String getBowelBarString(int bowelLevel) {
        int barLength = 10;
        int filledBars = bowelLevel/10; 

        StringBuilder bowelBar = new StringBuilder();
        for (int i = 0; i < filledBars; i++) {
            bowelBar.append("\uE052");
        }

        for (int i = filledBars; i < barLength; i++) {
            bowelBar.append("\uE053");
        }

        return bowelBar.toString();
    }

    static String getUnderwearStatus(int wetness, int fullness, int type, int designId, int size) {
        char[] stages = com.storynook.DesignRegistry.getStages(type, designId, size);
        int maxWetStages, maxMessStages;
        switch(type) {
        case 0: // Underwear
            maxWetStages = 1;
            maxMessStages = 1;
            break;
        case 1: // Pull-up
            maxWetStages = 3;
            maxMessStages = 1;
            break;
        case 2: // Diaper
            maxWetStages = 4;
            maxMessStages = 2;
            break;
        case 3: // Thick Diaper
            maxWetStages = 5;
            maxMessStages = 3;
            break;
        default:
            maxWetStages = 0;
            maxMessStages = 0;
            break;
        }
        int wetStage = Math.min((int) Math.floor(wetness / (100.0 / maxWetStages)), maxWetStages);
        int messStage = Math.min((int) Math.floor(fullness / (100.0 / maxMessStages)), maxMessStages);

        int stageIndex;

        if (wetStage > 0 && messStage > 0) {
            // Both wet and mess present - calculate combined state
            stageIndex = wetStage + (messStage * maxWetStages) + messStage + (maxMessStages - messStage);
        } else if (wetStage > 0) {
            // Only wet present
            stageIndex = wetStage;
        } else if (messStage > 0) {
            // Only mess present
            stageIndex = maxWetStages + messStage;
        } else {
            // Clean state
            stageIndex = 0;
        }

        // Ensure index doesn't exceed array bounds
        stageIndex = Math.min(stageIndex, stages.length - 1);
        
        return String.valueOf(stages[stageIndex]);
    }
    public static void createSidebar(Player player) {
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("stats", "dummy", " ");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);  // Show on the right side of the screen

        player.setScoreboard(board);
    }
    public static void updateSidebar(Plugin plugin, Player player, PlayerStats stats, Double bladderFill, Double bowelFill) {
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective("stats");

        if (objective != null) {
            board.getEntries().forEach(board::resetScores);

            String bladderBar = getBladderBarString((int)stats.getBladder());
            String underwearstate = getUnderwearStateString((int)stats.getDiaperWetness(), (int)stats.getDiaperFullness());
            String underwearImgage = getUnderwearStatus((int)stats.getDiaperWetness(), (int)stats.getDiaperFullness(), (int)stats.getUnderwearType(), stats.getUnderwearDesign(), 1);
            String fill = "\uE050 " + bladderFill + " | \uE052 " + bowelFill;

            if (stats.getshowunderwear()) {
                Score UnderwearStatus = objective.getScore(underwearImgage);
                UnderwearStatus.setScore(4);
            }
            if (stats.getfillbar()) {
                Score UnderwearStatusBar = objective.getScore(underwearstate);
                UnderwearStatusBar.setScore(3);
            }

            if (stats.getshowfill()&& !stats.getHardcore()) {
                Score fillsScore = objective.getScore(fill);
                fillsScore.setScore(2);
            }

            if(stats.getMessing()){
                String bowelsBar = getBowelBarString((int)stats.getBowels());
                Score bowelScore = objective.getScore(bowelsBar);
                bowelScore.setScore(1); 
            }

            Score bladderScore = objective.getScore(bladderBar);
            bladderScore.setScore(0);

            // Balance display in the sidebar title (above every entry). Disabled for
            // now -- uncomment the block below to re-enable. Supporting pieces
            // (PlaceholderAPI probe, Show_Balance/Balance_Placeholder config,
            // tryBuildBalanceLine helper, welcomebook section) are intentionally left
            // in place so re-enabling is just an uncomment.
            // if (!plugin.PlaceholderAPI) {
            //     org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            //     if (papi != null && papi.isEnabled()) {
            //         plugin.PlaceholderAPI = true;
            //     }
            // }
            // String balanceLine = null;
            // if (Boolean.TRUE.equals(plugin.getGlobalConfig().get("Show_Balance")) && plugin.PlaceholderAPI) {
            //     balanceLine = tryBuildBalanceLine(plugin, player);
            // }
            // objective.setDisplayName(balanceLine != null ? balanceLine : " ");

            // Score Overlay = objective.getScore("\uF001");
            // Overlay.setScore(8);

            // Score Logo = objective.getScore("\uF002");
            // Logo.setScore(9);

        }
    }
}
