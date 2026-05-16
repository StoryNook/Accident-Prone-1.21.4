package com.storynook.nanny.tasks;

/**
 * One-shot task with a TTL. Lives in the arbiter's transient queue, alongside
 * the permanently registered tasks. TTL is decremented once per arbiter tick.
 * When TTL hits 0 the wrapper is dropped regardless of whether the underlying
 * task ever won arbitration.
 */
public final class TransientTask {
    private final NannyTask delegate;
    private int ttl;

    public TransientTask(NannyTask delegate, int initialTTL) {
        this.delegate = delegate;
        this.ttl = initialTTL;
    }

    public NannyTask task() { return delegate; }
    public int ttl() { return ttl; }
    public void decrement() { ttl--; }
    public boolean expired() { return ttl <= 0; }
}
