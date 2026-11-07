// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

/** Main-thread debounce and rate bound, independent of UI/network availability. */
public final class AutomaticReviewGate {
    public static final long PAUSE_MS=900, INTERVAL_MS=3000;
    private long boundary=-1, started=-1;
    public void boundary(long now) { boundary=now; }
    public void invalidate() { boundary=-1; }
    public boolean ready(long now) { return boundary>=0&&delay(now)==0; }
    public long delay(long now) {
        if(boundary<0)return Long.MAX_VALUE;
        return Math.max(0,Math.max(boundary+PAUSE_MS,started<0?0:started+INTERVAL_MS)-now);
    }
    public void started(long now) { started=now;boundary=-1; }
}
