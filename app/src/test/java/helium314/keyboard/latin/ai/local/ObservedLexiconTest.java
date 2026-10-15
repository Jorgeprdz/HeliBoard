// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;
import org.junit.Test;import static org.junit.Assert.*;
public class ObservedLexiconTest {
 private Object source(){try{return Class.forName("helium314.keyboard.latin.ai.local.ObservedLexicon").getConstructor().newInstance();}catch(Exception e){throw new AssertionError("Dictionary observation bridge missing",e);}}
 private void record(Object source,String word,boolean valid,String...candidates){try{source.getClass().getMethod("record",String.class,boolean.class,List.class).invoke(source,word,valid,Arrays.asList(candidates));}catch(Exception e){throw new AssertionError(e);}}
 private LocalContextReviewer.Lexicon snapshot(Object source){try{return (LocalContextReviewer.Lexicon)source.getClass().getMethod("snapshot").invoke(source);}catch(Exception e){throw new AssertionError(e);}}
 @Test public void acceptsObservedWordsOfEitherLanguage(){Object s=source();record(s,"mañana",true);record(s,"meeting",true);assertTrue(snapshot(s).valid("mañana"));assertTrue(snapshot(s).valid("meeting"));}
 @Test public void snapshotCannotChangeDuringBackgroundReview(){Object s=source();record(s,"cotisacion",false,"cotización");LocalContextReviewer.Lexicon first=snapshot(s);record(s,"cotisacion",true);assertFalse(first.valid("cotisacion"));assertEquals(Arrays.asList("cotización"),first.candidates("cotisacion"));}
 @Test public void doesNotRetainSensitiveValues(){Object s=source();record(s,"123456",true,"123457");assertFalse(snapshot(s).valid("123456"));assertTrue(snapshot(s).candidates("123456").isEmpty());}
}
