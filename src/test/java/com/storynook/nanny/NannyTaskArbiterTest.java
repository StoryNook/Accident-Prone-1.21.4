package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.storynook.nanny.tasks.Candidate;
import com.storynook.nanny.tasks.NannyTask;
import com.storynook.nanny.tasks.Result;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

public class NannyTaskArbiterTest {

    @Test
    public void freshArbiter_isIdle() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        assertTrue(arbiter.isIdle());
    }

    @Test
    public void registerTask_increasesRegisteredCount() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        arbiter.register(new NoopTask());
        assertFalse(arbiter.registered().isEmpty());
    }

    @Test
    public void buildCandidates_sortsByPriorityDesc() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        arbiter.register(new FixedPriorityTask("low", 30));
        arbiter.register(new FixedPriorityTask("high", 70));
        arbiter.register(new FixedPriorityTask("mid", 50));

        List<NannyTaskArbiter.ScoredCandidate> sorted =
                arbiter.buildAndSortCandidates(null, null, java.util.Collections.singletonList((Player) null));

        // null-tolerant: tasks evaluate without crashing on null ward
        assertEquals(3, sorted.size());
        assertEquals("high", sorted.get(0).task().id());
        assertEquals("mid", sorted.get(1).task().id());
        assertEquals("low", sorted.get(2).task().id());
    }

    /** Minimal test fixture — a task that never evaluates to anything. */
    static class NoopTask implements NannyTask {
        @Override public String id() { return "noop"; }
        @Override public Candidate evaluate(NannyEntity n, NannyData d, Player w) { return null; }
        @Override public Result act(NannyEntity n, NannyData d, Player w) { return Result.DONE; }
    }

    /** Test fixture returning a fixed priority Candidate regardless of inputs. */
    static class FixedPriorityTask implements NannyTask {
        private final String id;
        private final int priority;
        FixedPriorityTask(String id, int priority) { this.id = id; this.priority = priority; }
        @Override public String id() { return id; }
        @Override public Candidate evaluate(NannyEntity n, NannyData d, Player w) {
            return new Candidate(priority, w, null, "test");
        }
        @Override public Result act(NannyEntity n, NannyData d, Player w) { return Result.DONE; }
    }
}
