package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.storynook.nanny.tasks.Candidate;
import com.storynook.nanny.tasks.NannyTask;
import com.storynook.nanny.tasks.Result;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class NannyTaskArbiterTest {

    private ServerMock server;
    private UUID ownerUUID;

    @BeforeEach
    public void setupServer() {
        server = MockBukkit.mock();
        ownerUUID = UUID.randomUUID();
    }

    @AfterEach
    public void teardownServer() {
        MockBukkit.unmock();
    }

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

    @Test
    public void tieBreak_ownerWardWinsOverNonOwner() {
        PlayerMock owner = server.addPlayer();
        PlayerMock other = server.addPlayer();
        // NannyData ctor in this branch is (UUID nannyUUID, UUID ownerUUID, String name, Plugin plugin).
        // Plugin is null-safe inside the constructor.
        NannyData data = new NannyData(UUID.randomUUID(), owner.getUniqueId(), "TestNanny", null);

        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        arbiter.register(new FixedPriorityTask("change", 50));

        List<NannyTaskArbiter.ScoredCandidate> sorted =
                arbiter.buildAndSortCandidates(null, data, List.of((Player) other, (Player) owner));

        // Both tasks evaluate to priority 50. Owner-tie-break should put owner-target first.
        assertEquals(2, sorted.size());
        assertEquals(owner.getUniqueId(), sorted.get(0).ward().getUniqueId());
    }

    @Test
    public void tieBreak_closerTargetWinsWhenWardAndPriorityTie() {
        PlayerMock owner = server.addPlayer();
        World world = server.addSimpleWorld("test");
        NannyData data = new NannyData(UUID.randomUUID(), owner.getUniqueId(), "TestNanny", null);
        // Pretend the Nanny is at origin
        Location nannyLoc = new Location(world, 0, 64, 0);

        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        // Two tasks with same priority + same ward but different target distances
        arbiter.register(new FixedTargetTask("far", 50, owner, new Location(world, 100, 64, 0)));
        arbiter.register(new FixedTargetTask("near", 50, owner, new Location(world, 10, 64, 0)));

        List<NannyTaskArbiter.ScoredCandidate> sorted =
                arbiter.buildAndSortCandidatesAt(nannyLoc, null, data, List.of((Player) owner));

        assertEquals(2, sorted.size());
        assertEquals("near", sorted.get(0).task().id());
    }

    static class FixedTargetTask implements NannyTask {
        private final String id;
        private final int priority;
        private final Player wardOverride;
        private final Location target;
        FixedTargetTask(String id, int priority, Player ward, Location target) {
            this.id = id; this.priority = priority; this.wardOverride = ward; this.target = target;
        }
        @Override public String id() { return id; }
        @Override public Candidate evaluate(NannyEntity n, NannyData d, Player w) {
            return new Candidate(priority, wardOverride, target, "test");
        }
        @Override public Result act(NannyEntity n, NannyData d, Player w) { return Result.DONE; }
    }

    @Test
    public void latch_incumbentWinsTies() {
        PlayerMock owner = server.addPlayer();
        PlayerMock other = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), owner.getUniqueId(), "TestNanny", null);
        NannyTaskArbiter arbiter = new NannyTaskArbiter();

        NannyTask incumbent = new FixedPriorityTask("incumbent", 50);
        NannyTask challenger = new FixedPriorityTask("challenger", 50);
        arbiter.register(incumbent);
        arbiter.register(challenger);

        // First tick: pick one as activeTask (priority-tie + non-owner → uses iteration order)
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) other)));
        String firstWinner = arbiter.activeTaskId();
        assertNotNull(firstWinner);

        // Second tick: same candidates. Incumbent should stay.
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) other)));
        assertEquals(firstWinner, arbiter.activeTaskId());
    }

    @Test
    public void latch_preemptOnHigherPriority() {
        PlayerMock ward = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TestNanny", null);
        NannyTaskArbiter arbiter = new NannyTaskArbiter();

        NannyTask low = new FixedPriorityTask("low", 30);
        NannyTask high = new FixedPriorityTask("high", 70);
        arbiter.register(low);

        // Tick 1: only "low" is registered → it becomes activeTask
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("low", arbiter.activeTaskId());

        // Add "high" mid-run
        arbiter.register(high);

        // Tick 2: high preempts low
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("high", arbiter.activeTaskId());
    }

    @Test
    public void latch_falsesIncumbentIfNoLongerEligible() {
        PlayerMock ward = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TestNanny", null);
        NannyTaskArbiter arbiter = new NannyTaskArbiter();

        // Use a toggleable task — eligible on first tick, not on second
        ToggleableTask change = new ToggleableTask("change", 90);
        NannyTask feed = new FixedPriorityTask("feed", 70);
        arbiter.register(change);
        arbiter.register(feed);

        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("change", arbiter.activeTaskId());

        change.setEligible(false);
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("feed", arbiter.activeTaskId());
    }

    static class ToggleableTask implements NannyTask {
        private final String id;
        private final int priority;
        private boolean eligible = true;
        ToggleableTask(String id, int priority) { this.id = id; this.priority = priority; }
        void setEligible(boolean v) { this.eligible = v; }
        @Override public String id() { return id; }
        @Override public Candidate evaluate(NannyEntity n, NannyData d, Player w) {
            return eligible ? new Candidate(priority, w, null, "test") : null;
        }
        @Override public Result act(NannyEntity n, NannyData d, Player w) { return Result.DONE; }
    }

    @Test
    public void applyActResult_DONE_clearsActiveTask() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        PlayerMock ward = server.addPlayer();
        arbiter.register(new FixedPriorityTask("x", 50));
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("x", arbiter.activeTaskId());
        arbiter.applyActResult(Result.DONE);
        assertNull(arbiter.activeTaskId());
    }

    @Test
    public void applyActResult_CONTINUE_keepsActiveTask() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        PlayerMock ward = server.addPlayer();
        arbiter.register(new FixedPriorityTask("x", 50));
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        arbiter.applyActResult(Result.CONTINUE);
        assertEquals("x", arbiter.activeTaskId());
    }

    @Test
    public void applyActResult_FAIL_GIVEUP_clearsAndSetsCooldown() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        PlayerMock ward = server.addPlayer();
        arbiter.register(new FixedPriorityTask("x", 50));
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        arbiter.applyActResult(Result.FAIL_GIVEUP);
        assertNull(arbiter.activeTaskId());
        // The (taskId, target=null) tuple should now be on cooldown — verified in Task 10 test.
    }

    @Test
    public void cooldown_excludesTaskAfterFailGiveup() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        PlayerMock ward = server.addPlayer();
        arbiter.register(new FixedPriorityTask("a", 50));
        arbiter.register(new FixedPriorityTask("b", 30));
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);

        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("a", arbiter.activeTaskId());
        arbiter.applyActResult(Result.FAIL_GIVEUP);

        // Next tick: "a" should be on cooldown, "b" should win
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("b", arbiter.activeTaskId());
    }

    @Test
    public void transient_eligibleForTTLThenDropped() {
        NannyTaskArbiter arbiter = new NannyTaskArbiter();
        PlayerMock ward = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);

        // Inject a transient task with TTL = 2 ticks
        arbiter.injectReactive(new FixedPriorityTask("transient", 100), 2);

        // Tick 1: should win (priority 100)
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("transient", arbiter.activeTaskId());
        arbiter.tickTransientTTL();

        // Tick 2: still eligible
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("transient", arbiter.activeTaskId());
        arbiter.tickTransientTTL();

        // Tick 3: dropped
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertNull(arbiter.activeTaskId());
    }

    @Test
    public void latch_sameTaskSameTargetLowerPriority_doesNotRegressPriority() {
        PlayerMock ward = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        NannyTaskArbiter arbiter = new NannyTaskArbiter();

        // Same id + same target (null), priority drops from 95 → 50 across ticks.
        VariablePriorityTask varTask = new VariablePriorityTask("var", 95, 50);
        arbiter.register(varTask);

        // Tick 1: var evaluates to 95 → activeTask priority=95
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("var", arbiter.activeTaskId());

        // Tick 2: var evaluates to 50 (same id+target) → rule 4 fires. Stored priority must
        // stay 95, otherwise a priority-60 challenger could preempt.
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("var", arbiter.activeTaskId());

        // Tick 3: register a priority-60 task. If rule 4 had regressed activeTask.priority to 50,
        // the new task would preempt. With the fix, "var" (still stored at 95) holds the latch.
        arbiter.register(new FixedPriorityTask("challenger60", 60));
        arbiter.applyLatch(arbiter.buildAndSortCandidates(null, data, List.of((Player) ward)));
        assertEquals("var", arbiter.activeTaskId());
    }

    /** Test fixture: same id + same target, but priority varies between ticks. */
    static class VariablePriorityTask implements NannyTask {
        private final String id;
        private final int[] priorities;
        private int tick = 0;
        VariablePriorityTask(String id, int... priorities) {
            this.id = id;
            this.priorities = priorities;
        }
        @Override public String id() { return id; }
        @Override public Candidate evaluate(NannyEntity n, NannyData d, Player w) {
            int p = priorities[Math.min(tick, priorities.length - 1)];
            tick++;
            return new Candidate(p, w, null, "test");
        }
        @Override public Result act(NannyEntity n, NannyData d, Player w) { return Result.DONE; }
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
