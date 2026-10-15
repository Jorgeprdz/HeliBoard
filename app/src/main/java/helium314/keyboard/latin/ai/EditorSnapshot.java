// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

/** Exact bounded editor target. A repeated string is not an editor/range identity. */
public final class EditorSnapshot {
    public final Object editor;
    public final String packageName;
    public final int fieldId;
    public final long generation, revision;
    public final int start, end;
    public final CharSequence original;
    private final String exactText;

    public EditorSnapshot(Object editor, String packageName, int fieldId, long generation,
                          long revision, int start, int end, CharSequence original) {
        this.editor = editor; this.packageName = packageName; this.fieldId = fieldId;
        this.generation = generation; this.revision = revision;
        this.start = start; this.end = end;
        this.original = original;
        exactText = original == null ? null : original.toString();
    }

    public boolean matches(EditorSnapshot other) {
        return other != null && editor != null && editor == other.editor
                && packageName != null && packageName.equals(other.packageName)
                && fieldId == other.fieldId && generation == other.generation
                && revision == other.revision && start == other.start && end == other.end
                && exactText != null && exactText.equals(other.exactText);
    }

    public boolean hasSelection() {
        return start >= 0 && end >= 0 && start != end && exactText != null
                && exactText.length() == Math.abs(end - start)
                && exactText.length() <= SensitiveInputPolicy.MAX_SELECTION;
    }
}
