// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import org.junit.Test;
import java.io.*;
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
}
