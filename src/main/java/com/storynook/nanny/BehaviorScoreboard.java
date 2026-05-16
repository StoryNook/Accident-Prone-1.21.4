package com.storynook.nanny;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Per-(Nanny, ward) behavior score and streak management.
 *
 * <p>Score is bidirectional in [-100, +100]; streak is bidirectional in [-50, +50].
 * Streak halves every real-time minute (lazy on access). Score decay (1 per Minecraft
 * day) is applied by NannyCareEngine on its tick — not here, since this class is
 * agnostic of world ticks.
 *
 * <p>Sycophancy gate: when {@code score < 0 AND streak > +20}, incoming positive
 * deltas are halved before applying. Prevents farming positive score while still in
 * negative territory via rapid-fire compliments.
 *
 * <p>Clock is injected via {@link LongSupplier} ({@code System::currentTimeMillis} in
 * production, atomic-controlled in tests) so streak decay is deterministic.
 */
public class BehaviorScoreboard {

    public static final int SCORE_FLOOR = -100;
    public static final int SCORE_CEIL  = +100;
    public static final int STREAK_FLOOR = -50;
    public static final int STREAK_CEIL  = +50;

    public static final int SYCOPHANCY_SCORE_THRESHOLD  = 0;
    public static final int SYCOPHANCY_STREAK_THRESHOLD = +20;
    public static final double SYCOPHANCY_DAMPEN_FACTOR = 0.5;

    public static final long STREAK_HALF_LIFE_MS = 60_000L;

    /**
     * Score-toward-zero drift cadence. The score moves 1 toward 0 every
     * SCORE_DRIFT_INTERVAL_MS of real time, lazily on access. Default 20 minutes
     * = one Minecraft day (matches the documented Score_Decay_Per_MCDay knob).
     */
    public static final long SCORE_DRIFT_INTERVAL_MS = 20L * 60_000L;

    /** Per-ward last-drift timestamp. Not persisted — resets to "now" on plugin restart. */
    private final java.util.Map<UUID, Long> scoreDriftLastTs = new java.util.concurrent.ConcurrentHashMap<>();

    private final LongSupplier nowSupplier;

    public BehaviorScoreboard(LongSupplier nowSupplier) {
        this.nowSupplier = nowSupplier;
    }

    public BehaviorScoreboard() {
        this(System::currentTimeMillis);
    }

    /**
     * Records a score-changing event. Applies streak decay first (lazy), then applies
     * any sycophancy dampening, then updates both score and streak.
     *
     * @param data       the Nanny whose perspective this is from
     * @param ward       the ward being scored
     * @param signal     informational tag (logged, not stored)
     * @param rawDelta   the unmodified delta; positive = nice, negative = naughty
     */
    public void record(NannyData data, UUID ward, String signal, int rawDelta) {
        applyStreakDecay(data, ward);
        applyScoreDrift(data, ward);
        int score = data.getBehaviorScore().getOrDefault(ward, 0);
        int streak = data.getBehaviorStreak().getOrDefault(ward, 0);

        // Reset streak to 0 when direction flips (positive↔negative), so streak
        // tracks *recent* intent rather than being offset by prior opposite behavior.
        if ((rawDelta > 0 && streak < 0) || (rawDelta < 0 && streak > 0)) {
            streak = 0;
        }

        // Streak always accumulates the raw delta (undampened) so it reliably
        // reflects recent enthusiasm regardless of whether score dampening fires.
        int newStreak = clamp(streak + rawDelta, STREAK_FLOOR, STREAK_CEIL);

        // Sycophancy gate: dampen positive score delta when the ward is not yet in
        // positive territory (score < 0) but has been piling on positives fast enough
        // to exceed the streak threshold. Prevents farming score from a naughty hole
        // via rapid-fire compliments.
        int effectiveDelta = rawDelta;
        if (rawDelta > 0
                && score < SYCOPHANCY_SCORE_THRESHOLD
                && newStreak > SYCOPHANCY_STREAK_THRESHOLD) {
            effectiveDelta = (int) Math.round(rawDelta * SYCOPHANCY_DAMPEN_FACTOR);
        }

        int newScore = clamp(score + effectiveDelta, SCORE_FLOOR, SCORE_CEIL);

        data.getBehaviorScore().put(ward, newScore);
        data.getBehaviorStreak().put(ward, newStreak);
    }

    public int getScore(NannyData data, UUID ward) {
        applyStreakDecay(data, ward);
        applyScoreDrift(data, ward);
        return data.getBehaviorScore().getOrDefault(ward, 0);
    }

    public int getStreak(NannyData data, UUID ward) {
        applyStreakDecay(data, ward);
        return data.getBehaviorStreak().getOrDefault(ward, 0);
    }

    /**
     * Drift the per-ward score 1 step toward 0 for every elapsed
     * {@link #SCORE_DRIFT_INTERVAL_MS} of real time since the last drift tick.
     * No-op when the score is already 0. Lazy on access; idempotent under no
     * elapsed time.
     */
    private void applyScoreDrift(NannyData data, UUID ward) {
        long now = nowSupplier.getAsLong();
        Long last = scoreDriftLastTs.get(ward);
        if (last == null) {
            scoreDriftLastTs.put(ward, now);
            return;
        }
        long elapsed = now - last;
        if (elapsed < SCORE_DRIFT_INTERVAL_MS) return;

        int ticks = (int) (elapsed / SCORE_DRIFT_INTERVAL_MS);
        int score = data.getBehaviorScore().getOrDefault(ward, 0);
        int sign = Integer.signum(score);
        int magnitude = Math.abs(score);
        int newMagnitude = Math.max(0, magnitude - ticks);
        int newScore = sign * newMagnitude;
        if (newScore != score) {
            data.getBehaviorScore().put(ward, newScore);
        }
        scoreDriftLastTs.put(ward, last + (long) ticks * SCORE_DRIFT_INTERVAL_MS);
    }

    /**
     * Halves the streak for every elapsed minute since last decay tick. Lazy:
     * called from {@link #record} and the getters so we never need a scheduler.
     */
    private void applyStreakDecay(NannyData data, UUID ward) {
        Long last = data.getBehaviorLastDecay().get(ward);
        long now = nowSupplier.getAsLong();
        if (last == null) {
            data.getBehaviorLastDecay().put(ward, now);
            return;
        }
        long elapsed = now - last;
        if (elapsed < STREAK_HALF_LIFE_MS) return;

        int halvings = (int) (elapsed / STREAK_HALF_LIFE_MS);
        int streak = data.getBehaviorStreak().getOrDefault(ward, 0);
        for (int i = 0; i < halvings && streak != 0; i++) {
            streak = streak / 2;  // integer division — drifts toward 0
        }
        data.getBehaviorStreak().put(ward, streak);
        data.getBehaviorLastDecay().put(ward, last + (long) halvings * STREAK_HALF_LIFE_MS);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
