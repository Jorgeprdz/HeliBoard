// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import java.text.Normalizer;
import java.util.*;

/** Bounded single-word transitions. Never stores editor text, sentences, or recording data. */
public final class PersonalTransitionModel {
    public static final int MAX_TRANSITIONS = 2048;
    public static final int MIN_OBSERVATIONS = 3;
    private static final int MAX_COUNT = 1_000_000;
    private final Map<String, Record> records = new LinkedHashMap<>();
    private long clock;

    public static final class Record {
        public final String previous, next, language;
        public final int observations;
        public final long updated;
        public Record(String previous, String next, String language, int observations, long updated) {
            this.previous=previous; this.next=next; this.language=language;
            this.observations=observations; this.updated=updated;
        }
        String key() { return previous+'\t'+next+'\t'+language; }
    }
    private static String normalize(String word) {
        return word == null ? "" : Normalizer.normalize(word, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    private static boolean word(String word) {
        return word.matches("[\\p{L}\\p{M}]{1,32}") && !SensitiveInputPolicy.sensitiveText(word);
    }
    private static boolean language(String language) {
        return "es-MX".equals(language) || "en-US".equals(language);
    }
    /** Caller must gate both learning consent and editor privacy before observing. */
    public synchronized Record observeTransition(String previous, String next, String language) {
        previous=normalize(previous); next=normalize(next);
        if (!language(language) || !word(previous) || !word(next)) return null;
        String key=previous+'\t'+next+'\t'+language;
        Record old=records.remove(key);
        clock=Math.max(clock+1, System.currentTimeMillis());
        records.put(key, new Record(previous,next,language,
                old==null ? 1 : Math.min(MAX_COUNT,old.observations+1),clock));
        prune();
        return records.get(key);
    }
    /** Returns at most eight candidates, after at least three observations of each pair. */
    public synchronized List<String> predict(String previous, String language, int limit) {
        previous=normalize(previous);
        if (!language(language) || !word(previous) || limit<=0) return Collections.emptyList();
        List<Record> candidates=new ArrayList<>();
        for (Record record:records.values()) {
            if(record.previous.equals(previous) && record.language.equals(language)
                    && record.observations>=MIN_OBSERVATIONS) candidates.add(record);
        }
        candidates.sort(Comparator.comparingInt((Record record)->record.observations).reversed()
                .thenComparing(Comparator.comparingLong((Record record)->record.updated).reversed())
                .thenComparing(record->record.next));
        List<String> result=new ArrayList<>();
        for (int i=0;i<Math.min(Math.min(limit,8),candidates.size());i++) result.add(candidates.get(i).next);
        return Collections.unmodifiableList(result);
    }
    public synchronized List<Record> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }
    /** Local SQLite restore only. Invalid/unbounded records are rejected, never promoted. */
    public synchronized void restore(Record record) {
        if(record==null || !language(record.language) || !word(record.previous) || !word(record.next)
                || !record.previous.equals(normalize(record.previous)) || !record.next.equals(normalize(record.next))
                || record.observations<1 || record.observations>MAX_COUNT || record.updated<0
                || record.updated>System.currentTimeMillis()+86_400_000L) return;
        Record old=records.get(record.key());
        if(old!=null && old.updated>record.updated) return;
        records.put(record.key(),record); clock=Math.max(clock,record.updated); prune();
    }
    private void prune() {
        while(records.size()>MAX_TRANSITIONS) {
            Record oldest=Collections.min(records.values(),Comparator.comparingLong(record->record.updated));
            records.remove(oldest.key());
        }
    }
    public synchronized void clear() { records.clear(); clock=0; }
}
