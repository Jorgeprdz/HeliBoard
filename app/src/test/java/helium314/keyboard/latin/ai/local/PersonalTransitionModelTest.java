// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PersonalTransitionModelTest {
    private Object model() throws Exception {
        return Class.forName("helium314.keyboard.latin.ai.local.PersonalTransitionModel").getConstructor().newInstance();
    }
    private void observe(Object model, String previous, String next, String language) throws Exception {
        model.getClass().getMethod("observeTransition", String.class, String.class, String.class)
                .invoke(model, previous, next, language);
    }
    @SuppressWarnings("unchecked")
    private List<String> predict(Object model, String previous, String language, int limit) throws Exception {
        return (List<String>) model.getClass().getMethod("predict", String.class, String.class, int.class)
                .invoke(model, previous, language, limit);
    }
    @Test public void requiresRepeatedEvidenceAndRanksByFrequency() throws Exception {
        Object model = model();
        for (int i=0; i<2; i++) observe(model, "buenos", "días", "es-MX");
        assertTrue(predict(model,"buenos","es-MX",3).isEmpty());
        observe(model,"Buenos","DÍAS","es-MX");
        for (int i=0;i<4;i++) observe(model,"buenos","amigos","es-MX");
        assertEquals(Arrays.asList("amigos","días"),predict(model,"BUENOS","es-MX",3));
        assertEquals(Collections.singletonList("amigos"),predict(model,"buenos","es-MX",1));
    }
    @Test public void neverCrossesLanguageOrAcceptsPhrasesSecretsOrNumbers() throws Exception {
        Object model=model();
        for(int i=0;i<5;i++) {
            observe(model,"hello","world","en-US");
            observe(model,"hello","full phrase","en-US");
            observe(model,"hello","password","en-US");
            observe(model,"hello","1234","en-US");
            observe(model,"hello","world","arbitrary");
        }
        assertEquals(Collections.singletonList("world"),predict(model,"hello","en-US",10));
        assertTrue(predict(model,"hello","es-MX",10).isEmpty());
        assertTrue(predict(model,"hello","en-US",0).isEmpty());
    }
    @Test public void snapshotRestoresCountsAndClearRemovesAllEvidence() throws Exception {
        Object first=model();for(int i=0;i<3;i++)observe(first,"see","you","en-US");
        Object second=model();
        List<?> records=(List<?>)first.getClass().getMethod("snapshot").invoke(first);
        for(Object record:records) second.getClass().getMethod("restore",record.getClass()).invoke(second,record);
        assertEquals(Collections.singletonList("you"),predict(second,"see","en-US",3));
        second.getClass().getMethod("clear").invoke(second);
        assertTrue(predict(second,"see","en-US",3).isEmpty());
    }
    @Test public void evictsOldTransitionsAndBoundsPredictionCount() throws Exception {
        Object model=model();
        for(int i=0;i<2200;i++) {
            String word="word"+(char)('a'+i/676)+(char)('a'+i/26%26)+(char)('a'+i%26);
            for(int n=0;n<3;n++)observe(model,"next",word,"en-US");
        }
        List<?> records=(List<?>)model.getClass().getMethod("snapshot").invoke(model);
        assertTrue(records.size()<=2048);
        assertTrue(predict(model,"next","en-US",999).size()<=8);
        assertFalse(predict(model,"next","en-US",8).contains("wordaaa"));
    }
}
