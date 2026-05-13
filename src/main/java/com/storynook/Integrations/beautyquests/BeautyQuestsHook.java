package com.storynook.Integrations.beautyquests;

import com.storynook.Integrations.events.AccidentProneActionEvent;
import com.storynook.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * BeautyQuests integration. Trigger map values are:
 *   "questId"                       -> quest, branch 0, stage 0, single-fire
 *   "questId:branchId:stageId"      -> specific stage, single-fire (finishes after first hit)
 *   "questId:branchId:stageId:N"    -> specific stage, requires N hits to finish
 *                                      (in-memory counter per (player, questId:branchId:stageId);
 *                                       resets on server restart and on stage finish)
 *
 * For "do something N times" quests (e.g. "throw 5 dirty diapers in a pail"), use the
 * 4-segment form. Each event with ctx.count = M counts as M hits, so depositing 3
 * diapers in a single pail close advances the counter by 3.
 *
 * Find your quest ID in BeautyQuests: /quests editor list -> the leading number.
 */
public class BeautyQuestsHook implements Listener {
    private final Plugin plugin;
    private boolean initialised = false;
    // Per-player progress counters for count-based stages. Key:
    //   playerUuid + "@" + questId + ":" + branchId + ":" + stageId
    private final java.util.Map<String, Integer> stageCounters = new java.util.concurrent.ConcurrentHashMap<>();

    private Method getApiMethod;
    private Method getQuestsManagerMethod;
    private Method getQuestMethod;
    private Method getBranchesManagerMethod;
    private Method getBranchMethod;
    private Method getRegularStageMethod;
    private Method getApplicableQuestersMethod;
    private Method finishStageMethod;

    public BeautyQuestsHook(Plugin plugin) { this.plugin = plugin; }

    private synchronized void initIfNeeded() {
        if (initialised) return;
        initialised = true;
        if (plugin == null) return;
        try {
            Class<?> providerCls = Class.forName("fr.skytasul.quests.QuestsAPIProvider");
            Class<?> apiCls = Class.forName("fr.skytasul.quests.api.QuestsAPI");
            Class<?> questsManagerCls = Class.forName("fr.skytasul.quests.api.quests.QuestsManager");
            Class<?> questCls = Class.forName("fr.skytasul.quests.api.quests.Quest");
            Class<?> branchesManagerCls = Class.forName("fr.skytasul.quests.api.quests.branches.QuestBranchesManager");
            Class<?> branchCls = Class.forName("fr.skytasul.quests.api.quests.branches.QuestBranch");
            Class<?> stageCtrlCls = Class.forName("fr.skytasul.quests.api.stages.StageController");

            getApiMethod = providerCls.getMethod("getAPI");
            getQuestsManagerMethod = apiCls.getMethod("getQuestsManager");
            getQuestMethod = questsManagerCls.getMethod("getQuest", int.class);
            getBranchesManagerMethod = questCls.getMethod("getBranchesManager");
            getBranchMethod = branchesManagerCls.getMethod("getBranch", int.class);
            getRegularStageMethod = branchCls.getMethod("getRegularStage", int.class);
            getApplicableQuestersMethod = stageCtrlCls.getMethod(
                    "getApplicableQuesters", org.bukkit.entity.Player.class);

            // finishStage(Quester) - parameter type resolved by name match
            for (Method m : stageCtrlCls.getMethods()) {
                if ("finishStage".equals(m.getName()) && m.getParameterCount() == 1) {
                    finishStageMethod = m;
                    break;
                }
            }
            if (finishStageMethod == null) {
                throw new NoSuchMethodException("StageController.finishStage(Quester)");
            }

            plugin.getLogger().info("[BeautyQuestsHook] reflection initialised (BeautyQuests 2.x API)");
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "[BeautyQuestsHook] reflection probe failed: " + t.getClass().getSimpleName()
                            + ": " + t.getMessage() + "; quest progression disabled.");
            finishStageMethod = null;
        }
    }

    @EventHandler
    public void onAction(AccidentProneActionEvent event) {
        if (plugin == null) return;
        java.util.Map<String, Object> icfg = plugin.getIntegrationsConfig();
        if (icfg == null) return;
        if (!Boolean.TRUE.equals(icfg.get("BeautyQuests_enabled"))) return;
        initIfNeeded();
        if (finishStageMethod == null) return;

        String mapping = plugin.getBeautyQuestsTriggerMap().get(event.getActionId());
        if (mapping == null || mapping.isEmpty()) return;

        int questId, branchId = 0, stageId = 0, requiredCount = 1;
        try {
            String[] parts = mapping.split(":");
            questId = Integer.parseInt(parts[0].trim());
            if (parts.length >= 2) branchId = Integer.parseInt(parts[1].trim());
            if (parts.length >= 3) stageId = Integer.parseInt(parts[2].trim());
            if (parts.length >= 4) requiredCount = Math.max(1, Integer.parseInt(parts[3].trim()));
        } catch (NumberFormatException nfe) {
            plugin.getLogger().warning("[BeautyQuestsHook] bad trigger format '" + mapping
                    + "' for " + event.getActionId()
                    + " (expected 'questId' | 'questId:branchId:stageId' | 'questId:branchId:stageId:N')");
            return;
        }

        // ctx.count lets a single event represent N actions (e.g. depositing 3 dirty
        // items in one pail close fires once with count=3).
        int hitCount = 1;
        Object countObj = event.getCtx().get("count");
        if (countObj instanceof Number) hitCount = Math.max(1, ((Number) countObj).intValue());

        try {
            Object api = getApiMethod.invoke(null);
            if (api == null) return;
            Object questsManager = getQuestsManagerMethod.invoke(api);
            if (questsManager == null) return;
            Object quest = getQuestMethod.invoke(questsManager, questId);
            if (quest == null) return;
            Object branchesManager = getBranchesManagerMethod.invoke(quest);
            if (branchesManager == null) return;
            Object branch = getBranchMethod.invoke(branchesManager, branchId);
            if (branch == null) return;
            Object stageCtrl = getRegularStageMethod.invoke(branch, stageId);
            if (stageCtrl == null) return;

            Object questersObj = getApplicableQuestersMethod.invoke(stageCtrl, event.getWorker());
            if (!(questersObj instanceof Collection)) return;
            Collection<?> questers = (Collection<?>) questersObj;
            if (questers.isEmpty()) return;

            String counterKey = event.getWorker().getUniqueId() + "@" + questId + ":" + branchId + ":" + stageId;

            if (requiredCount <= 1) {
                // Single-fire mode (legacy / default).
                for (Object quester : questers) {
                    finishStageMethod.invoke(stageCtrl, quester);
                }
                stageCounters.remove(counterKey);  // safety; counter shouldn't exist for 1-required stages
                return;
            }

            // Count-based mode: accumulate hits until the threshold is reached.
            int previous = stageCounters.getOrDefault(counterKey, 0);
            int now = previous + hitCount;
            if (now >= requiredCount) {
                stageCounters.remove(counterKey);
                for (Object quester : questers) {
                    finishStageMethod.invoke(stageCtrl, quester);
                }
            } else {
                stageCounters.put(counterKey, now);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[BeautyQuestsHook] dispatch failed for " + mapping + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
