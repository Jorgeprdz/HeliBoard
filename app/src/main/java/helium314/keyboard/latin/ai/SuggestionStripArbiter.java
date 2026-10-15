// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

/** Owns deliberate temporary strip content; predictions continue and are restored on exit. */
public final class SuggestionStripArbiter {
    private boolean active, toolbarBefore;
    private Object predictions;
    public void enter(boolean toolbarVisible) {
        if (!active) toolbarBefore = toolbarVisible;
        active = true;
    }
    public boolean active() { return active; }
    public void rememberPredictions(Object value) { predictions = value; }
    public Object latestPredictions() { return predictions; }
    public void exit() { active = false; }
    public boolean restoreToolbarVisible() { return toolbarBefore; }
    public static boolean usesAiAccess(boolean enabled, boolean pinned, boolean customShort, boolean customLong) {
        return enabled && pinned && !customShort && !customLong;
    }
}
