package com.storynook;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import com.storynook.PlayerStatsManagement.PlayerStats;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Bukkit;

public class ScoreBoard {

    private static ScoreboardManager manager = Bukkit.getScoreboardManager();


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

    static String getUnderwearStatus(int wetness, int fullness, int type, int size) {
        // Define character mappings for each underwear type
        char[][] smallStages = { //DONE
            {'\uE134', '\uE136', '\uE135', '\uE137'}, // Underwear DONE
            {'\uE11c', '\uE124', '\uE12c', '\uE130', '\uE11f', '\uE127', '\uE12f', '\uE133'}, // Pull-up DONE
            {'\uE11c', '\uE120', '\uE124', '\uE12c', '\uE130', '\uE11d', '\uE11f', '\uE121', '\uE125', '\uE12d', '\uE131', '\uE123', '\uE127', '\uE12f', '\uE133'}, // Diaper DONE
            {'\uE11c', '\uE120', '\uE124', '\uE128', '\uE12c', '\uE130', '\uE11d', '\uE11e', '\uE11f', '\uE121', '\uE125', '\uE129', '\uE12d', '\uE131', '\uE122', '\uE126', '\uE12a', '\uE12e', '\uE132', '\uE123', '\uE127', '\uE12b', '\uE12f', '\uE133'} //Thick Diaper DONE
        };//DONE
        
        char[][] normalStages = {//DONE
            {'\uE018', '\uE01a', '\uE019', '\uE01b'}, //Underwear DONE
            {'\uE000', '\uE008', '\uE010', '\uE014', '\uE003', '\uE00b', '\uE013', '\uE017'}, // Pull-up DONE
            {'\uE000', '\uE004', '\uE008', '\uE010', '\uE014', '\uE001', '\uE003', '\uE005', '\uE009', '\uE011', '\uE015', '\uE007', '\uE00b', '\uE013', '\uE017'}, // Diaper DONE
            {'\uE000', '\uE004', '\uE008', '\uE00c', '\uE010', '\uE014', '\uE001', '\uE002', '\uE003', '\uE005', '\uE009', '\uE00d', '\uE011', '\uE015', '\uE006', '\uE00a', '\uE00e', '\uE012', '\uE016', '\uE007', '\uE00b', '\uE00f', '\uE013', '\uE017'} //Thick Diaper DONE
        };//DONE
        char[][] bigStages = {//DONE
            {'\uE034', '\uE036', '\uE035', '\uE037'}, //Underwear DONE
            {'\uE01c', '\uE024', '\uE02c', '\uE030', '\uE01f', '\uE027', '\uE02f', '\uE033'}, // Pull-up DONE
            {'\uE01c', '\uE020', '\uE024', '\uE02c', '\uE030', '\uE01d', '\uE01f', '\uE021', '\uE025', '\uE02d', '\uE031', '\uE023', '\uE027', '\uE02f', '\uE033'}, // Diaper DONE
            {'\uE01c', '\uE020', '\uE024', '\uE028', '\uE02c', '\uE030', '\uE01d', '\uE01e', '\uE01f', '\uE021', '\uE025', '\uE029', '\uE02d', '\uE031', '\uE022', '\uE026', '\uE02a', '\uE02e', '\uE032', '\uE023', '\uE027', '\uE02b', '\uE02f', '\uE033'} //Thick Diaper DONE
        };//DONE
        char[] stages;
        // Select the appropriate stage array
        switch (size) {
            case 0:
                stages = smallStages[type];
                break;
            case 1:
                stages = normalStages[type];
                break;
            case 2:
                stages = bigStages[type];
                break;
            default:
                stages = null;
                break;
        }
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
    public static void updateSidebar(Player player, PlayerStats stats, Double bladderFill, Double bowelFill) {
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective("stats");

        if (objective != null) {
            board.getEntries().forEach(board::resetScores);

            String bladderBar = getBladderBarString((int)stats.getBladder());
            String underwearstate = getUnderwearStateString((int)stats.getDiaperWetness(), (int)stats.getDiaperFullness());
            String underwearImgage = getUnderwearStatus((int)stats.getDiaperWetness(), (int)stats.getDiaperFullness(), (int)stats.getUnderwearType(), 1);
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
            
            // Score Overlay = objective.getScore("\uF001");
            // Overlay.setScore(8);

            // Score Logo = objective.getScore("\uF002");
            // Logo.setScore(9);

        }
    }
}
