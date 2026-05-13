package com.storynook.Integrations.jobs;

import com.storynook.Integrations.events.AccidentProneActionEvent;
import com.storynook.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;

public class AdvancedJobsHook implements Listener {
    private final Plugin plugin;
    private boolean initialised = false;

    private Method getInstanceMethod;
    private Method getJobsPipelineMethod;
    private Method handleMethod;
    private Constructor<?> actionExecutionCtor;
    private Constructor<?> actionResultCtor;

    public AdvancedJobsHook(Plugin plugin) { this.plugin = plugin; }

    private synchronized void initIfNeeded() {
        if (initialised) return;
        initialised = true;
        if (plugin == null) return;
        try {
            Class<?> coreCls = Class.forName("net.advancedplugins.jobs.Core");
            Class<?> pipelineCls = Class.forName("net.advancedplugins.jobs.jobs.JobsPipeline");
            Class<?> aeCls = Class.forName("net.advancedplugins.jobs.impl.actions.ActionExecution");
            Class<?> earCls = Class.forName(
                    "net.advancedplugins.jobs.impl.actions.objects.variable.ExecutableActionResult");

            getInstanceMethod = coreCls.getMethod("getInstance");
            getJobsPipelineMethod = coreCls.getMethod("getJobsPipeline");
            handleMethod = pipelineCls.getMethod("handle", aeCls);
            actionResultCtor = earCls.getDeclaredConstructor();
            actionExecutionCtor = aeCls.getConstructor(
                    org.bukkit.entity.Player.class,
                    String.class,
                    BigDecimal.class,
                    boolean.class,
                    earCls);

            plugin.getLogger().info("[AdvancedJobsHook] reflection initialised (net.advancedplugins.jobs.Core)");
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "[AdvancedJobsHook] reflection probe failed: " + t.getClass().getSimpleName()
                            + ": " + t.getMessage() + "; payouts disabled.");
            handleMethod = null;
        }
    }

    @EventHandler
    public void onAction(AccidentProneActionEvent event) {
        if (plugin == null) return;
        java.util.Map<String, Object> icfg = plugin.getIntegrationsConfig();
        if (icfg == null) return;
        if (!Boolean.TRUE.equals(icfg.get("Jobs_enabled"))) return;
        initIfNeeded();
        if (handleMethod == null) return;

        String ajAction = plugin.getJobsActionMap().get(event.getActionId());
        if (ajAction == null || ajAction.isEmpty()) return;

        try {
            Object core = getInstanceMethod.invoke(null);
            if (core == null) return;
            Object pipeline = getJobsPipelineMethod.invoke(core);
            if (pipeline == null) return;

            Object actionResult = actionResultCtor.newInstance();
            // Honor ctx.count (e.g. multiple pail deposits in one session) so a
            // single event awards N progress.
            int count = 1;
            Object countObj = event.getCtx().get("count");
            if (countObj instanceof Number) count = Math.max(1, ((Number) countObj).intValue());
            BigDecimal progress = BigDecimal.valueOf(event.getSuggestedMultiplier() * count);
            Object execution = actionExecutionCtor.newInstance(
                    event.getWorker(),
                    ajAction,
                    progress,
                    false,
                    actionResult);

            handleMethod.invoke(pipeline, execution);
        } catch (Throwable t) {
            plugin.getLogger().warning("[AdvancedJobsHook] dispatch failed for " + ajAction + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
