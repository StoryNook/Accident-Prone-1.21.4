package com.storynook.nanny.tasks;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Result of a task's evaluate() call. priority is integer (higher = more
 * urgent), target is null for tasks with no spatial target (e.g.
 * RecycleCursedPantsTask — pure inventory routing).
 */
public record Candidate(int priority, Player ward, Location target, String reasonTag) {}
