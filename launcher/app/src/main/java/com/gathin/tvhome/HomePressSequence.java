package com.gathin.tvhome;

/** Counts completed physical Home presses, never the held-key repeats. */
final class HomePressSequence {
    static final long GAP_MS = 650;
    private int count;
    private long last = -1;
    boolean press(long now) {
        count = last >= 0 && now >= last && now - last <= GAP_MS ? count + 1 : 1;
        last = now;
        if (count < 3) return false;
        reset();
        return true;
    }
    void reset() { count = 0; last = -1; }
}
