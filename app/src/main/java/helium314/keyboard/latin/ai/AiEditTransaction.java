// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

/** Main-thread confirmed edit and one-shot exact undo, invalidated by editor/content changes. */
public final class AiEditTransaction {
    public interface Target {
        EditorSnapshot capture();
        boolean replace(int start, int end, CharSequence value);
    }
    private EditorSnapshot original, after;
    private int replacementLength;

    public boolean apply(EditorSnapshot expected, CharSequence replacement, Target target) {
        clear();
        if (expected == null || !expected.hasSelection() || replacement == null
                || replacement.length() == 0 || replacement.length() > 16384
                || target == null || !expected.matches(target.capture())) return false;
        int start = Math.min(expected.start, expected.end), end = Math.max(expected.start, expected.end);
        if (!target.replace(start, end, replacement)) return false;
        original = expected; replacementLength = replacement.length(); after = target.capture();
        return true;
    }

    /** Explicit local correction or dictation. Target must validate the exact original span. */
    public boolean applySpan(EditorSnapshot expected,int start,int end,CharSequence originalText,
                             CharSequence replacement,Target target) {
        clear();
        if(expected==null||expected.start!=expected.end||expected.start<0||start<0||end<start||end>expected.start
            ||originalText==null||originalText.length()!=end-start||originalText.length()>4096
            ||replacement==null||replacement.length()==0||replacement.length()>16384
            ||target==null||!expected.matches(target.capture()))return false;
        if(start==end&&start!=expected.start)return false;
        if(!target.replace(start,end,replacement))return false;
        original=new EditorSnapshot(expected.editor,expected.packageName,expected.fieldId,expected.generation,
            expected.revision,start,end,originalText);
        replacementLength=replacement.length();after=target.capture();return true;
    }

    public boolean undo(Target target) {
        if (original == null || after == null || target == null || !after.matches(target.capture())) return false;
        int start = Math.min(original.start, original.end);
        if (!target.replace(start, start + replacementLength, original.original)) return false;
        clear(); return true;
    }

    public boolean hasUndo() { return original != null && after != null; }
    public void clear() { original = null; after = null; replacementLength = 0; }
}
