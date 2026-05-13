package com.storynook.Integrations.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

public class AccidentProneActionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player worker;
    private final Player target; // nullable
    private final String actionId;
    private final Map<String, Object> ctx;
    private double suggestedMultiplier = 1.0;
    private boolean cancelled = false;

    public AccidentProneActionEvent(Player worker, Player target, String actionId, Map<String, Object> ctx) {
        this.worker = worker;
        this.target = target;
        this.actionId = actionId;
        this.ctx = ctx == null ? Collections.<String, Object>emptyMap() : ctx;
    }

    public Player getWorker() { return worker; }
    public Player getTarget() { return target; }
    public String getActionId() { return actionId; }
    public Map<String, Object> getCtx() { return ctx; }
    public double getSuggestedMultiplier() { return suggestedMultiplier; }
    public void setSuggestedMultiplier(double m) { this.suggestedMultiplier = m; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean c) { this.cancelled = c; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
