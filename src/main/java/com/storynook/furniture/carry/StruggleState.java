package com.storynook.furniture.carry;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-ward struggle detection: a ring buffer of recent sneak/jump
 * timestamps. 4 events within 40 ticks fires a 100-tick burst with a
 * 100-tick post-burst cooldown.
 */
public class StruggleState {

    private static final int REQUIRED_EVENTS = 4;
    private static final long WINDOW_TICKS = 40L;
    private static final long BURST_TICKS = 100L;
    private static final long COOLDOWN_TICKS = 100L;

    private final Deque<Long> events = new ArrayDeque<>();
    private long burstEndsTick = Long.MIN_VALUE;
    private long cooldownEndsTick = Long.MIN_VALUE;

    /**
     * Records a sneak or jump event at the given tick. Returns true if
     * this event fires a new burst.
     */
    public boolean recordEvent(long currentTick) {
        if (currentTick < cooldownEndsTick) {
            return false;
        }
        events.addLast(currentTick);
        while (!events.isEmpty() && events.peekFirst() < currentTick - WINDOW_TICKS) {
            events.removeFirst();
        }
        if (events.size() >= REQUIRED_EVENTS) {
            burstEndsTick = currentTick + BURST_TICKS;
            cooldownEndsTick = burstEndsTick + COOLDOWN_TICKS;
            events.clear();
            return true;
        }
        return false;
    }

    public boolean isBurstActive(long currentTick) {
        return currentTick < burstEndsTick;
    }

    public long burstEndsTick() { return burstEndsTick; }
}
