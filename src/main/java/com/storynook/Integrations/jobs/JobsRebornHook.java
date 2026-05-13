package com.storynook.Integrations.jobs;

import com.storynook.Integrations.events.AccidentProneActionEvent;
import com.storynook.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;

public class JobsRebornHook implements Listener {
    private final Plugin plugin;
    private boolean reflectionTried = false;
    private Method payActionsMethod;

    public JobsRebornHook(Plugin plugin) { this.plugin = plugin; }

    private synchronized void initReflectionIfNeeded(Object jobsPlayer) {
        if (reflectionTried) return;
        reflectionTried = true;
        if (jobsPlayer == null || plugin == null) return;
        try {
            payActionsMethod = jobsPlayer.getClass().getMethod(
                    "payActions", String.class, double.class);
        } catch (NoSuchMethodException nsme) {
            plugin.getLogger().warning(
                "[JobsRebornHook] payActions(String,double) not found on this Jobs Reborn version; payouts disabled.");
            payActionsMethod = null;
        } catch (Throwable t) {
            plugin.getLogger().warning("[JobsRebornHook] reflection probe failed: " + t.getMessage());
            payActionsMethod = null;
        }
    }

    @EventHandler
    public void onAction(AccidentProneActionEvent event) {
        if (plugin == null) return;
        java.util.Map<String, Object> icfg = plugin.getIntegrationsConfig();
        if (icfg == null) return;
        if (!Boolean.TRUE.equals(icfg.get("Jobs_enabled"))) return;

        String jobsAction = plugin.getJobsActionMap().get(event.getActionId());
        if (jobsAction == null || jobsAction.isEmpty()) return;

        try {
            // Lookup JR's instance + JobsPlayer
            Class<?> jobsCls = Class.forName("com.gamingmesh.jobs.Jobs");
            Object jobsInstance = jobsCls.getMethod("getInstance").invoke(null);
            if (jobsInstance == null) return;
            Object playerManager = jobsCls.getMethod("getPlayerManager").invoke(jobsInstance);
            if (playerManager == null) return;
            Method getJP = playerManager.getClass().getMethod("getJobsPlayer", org.bukkit.entity.Player.class);
            Object jobsPlayer = getJP.invoke(playerManager, event.getWorker());
            if (jobsPlayer == null) return;

            initReflectionIfNeeded(jobsPlayer);
            if (payActionsMethod == null) return;

            payActionsMethod.invoke(jobsPlayer, jobsAction, event.getSuggestedMultiplier());
        } catch (ClassNotFoundException cnfe) {
            // Jobs Reborn not installed; this is fine -- the probe in Plugin.onEnable
            // shouldn't have registered us, but tolerate the case.
        } catch (Throwable t) {
            plugin.getLogger().warning("[JobsRebornHook] dispatch failed: " + t.getMessage());
        }
    }
}
