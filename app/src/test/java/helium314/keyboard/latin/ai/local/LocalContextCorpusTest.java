// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import org.junit.Test;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Synthetic rule corpus, not user messages or a measure of broad semantic understanding. */
public class LocalContextCorpusTest {
    static List<String[]> corpus() throws IOException {
        InputStream in = LocalContextCorpusTest.class.getResourceAsStream("/ai/local/bilingual-corpus.tsv");
        if (in == null) throw new IOException("missing corpus");
        List<String[]> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#") && !line.isEmpty()) cases.add(line.split("\t", -1));
            }
        }
        return cases;
    }
    static Set<String> annotated(String value) {
        return value.isEmpty() || value.equals("-") ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(value.split(";")));
    }
    static Set<String> proposals(List<LocalContextReviewer.Correction> corrections) {
        Set<String> result = new HashSet<>();
        for (LocalContextReviewer.Correction c : corrections) result.add(c.start + ":" + c.end + "=" + c.replacement);
        return result;
    }
    @Test public void labeledBilingualCorpusMatchesExactSpansWithoutFalsePositives() throws Exception {
        LocalContextReviewer reviewer = new LocalContextReviewer();
        for (String[] c : corpus()) {
            assertEquals(c[0] + ": " + c[1], annotated(c[3]), proposals(reviewer.review(c[1],
                    LocalContextReviewerTest.LEXICON, Collections.emptySet(), LocalContextReviewerTest.ACTIVE)));
        }
    }
    /** Standalone empirical benchmark; call with resource directory on classpath. */
    public static void main(String[] args) throws Exception {
        List<String[]> cases = corpus();
        LocalContextReviewer reviewer = new LocalContextReviewer();
        int tp = 0, fp = 0, fn = 0, exact = 0, negatives = 0, negativeFalsePositives = 0;
        for (String[] c : cases) {
            Set<String> expected = annotated(c[3]);
            Set<String> actual = proposals(reviewer.review(c[1], LocalContextReviewerTest.LEXICON,
                    Collections.emptySet(), LocalContextReviewerTest.ACTIVE));
            for (String proposal : actual) { if (expected.contains(proposal)) tp++; else fp++; }
            for (String target : expected) if (!actual.contains(target)) fn++;
            if (expected.equals(actual)) exact++;
            if (expected.isEmpty()) { negatives++; if (!actual.isEmpty()) negativeFalsePositives++; }
        }
        int warmup = 2000, count = 10000;
        for (int i = 0; i < warmup; i++) reviewer.review(cases.get(i % cases.size())[1],
                LocalContextReviewerTest.LEXICON, Collections.emptySet(), LocalContextReviewerTest.ACTIVE);
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean.isCurrentThreadCpuTimeSupported() && !bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
        long cpuBefore = bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1;
        long[] nanos = new long[count];
        int consumed = 0;
        for (int i = 0; i < count; i++) {
            long start = System.nanoTime();
            consumed += reviewer.review(cases.get(i % cases.size())[1], LocalContextReviewerTest.LEXICON,
                    Collections.emptySet(), LocalContextReviewerTest.ACTIVE).size();
            nanos[i] = System.nanoTime() - start;
        }
        long cpuAfter = bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1;
        Arrays.sort(nanos);
        System.out.println("SYNTHETIC BILINGUAL RULE CORPUS; fixture lexicon, not native device dictionary");
        System.out.printf(Locale.ROOT, "cases=%d exact=%d true_proposals=%d false_proposals=%d missed=%d%n", cases.size(), exact, tp, fp, fn);
        System.out.printf(Locale.ROOT, "proposal_precision=%.4f proposal_recall=%.4f negative_false_positive_cases=%d/%d%n",
                tp / (double) Math.max(1, tp + fp), tp / (double) Math.max(1, tp + fn), negativeFalsePositives, negatives);
        System.out.printf(Locale.ROOT, "WARM HOST JVM ONLY: java=%s arch=%s warmup=%d samples=%d consumed=%d%n",
                System.getProperty("java.version"), System.getProperty("os.arch"), warmup, count, consumed);
        System.out.printf(Locale.ROOT, "wall_microseconds p50=%.3f p95=%.3f p99=%.3f max=%.3f%n",
                nanos[count / 2] / 1000.0, nanos[(int)(count * .95)] / 1000.0,
                nanos[(int)(count * .99)] / 1000.0, nanos[count - 1] / 1000.0);
        if (cpuBefore >= 0) System.out.printf(Locale.ROOT, "thread_CPU_microseconds_per_review=%.3f%n", (cpuAfter - cpuBefore) / 1000.0 / count);
        System.out.println("No Android/IME baseline, frame latency, native lexicon latency, PSS, battery, or device p95 measured.");
    }
}
