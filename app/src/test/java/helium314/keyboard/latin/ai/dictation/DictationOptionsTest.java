// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class DictationOptionsTest {
 private List<String> words(String text){try{return (List<String>)Class.forName("helium314.keyboard.latin.ai.dictation.DictationOptions").getMethod("vocabulary",String.class).invoke(null,text);}catch(java.lang.reflect.InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(Exception e){throw new AssertionError("Dictation options missing",e);}}
 @Test public void preservesExplicitNamesAndTechnicalVocabulary(){assertEquals(Arrays.asList("Carlos","HeliBoard","One UI"),words("Carlos, HeliBoard\nOne UI;Carlos"));}
 @Test public void sensitiveVocabularyCannotReachProvider(){try{words("Carlos,CLABE 032180000118359719");fail("Must reject banking hints");}catch(IllegalArgumentException expected){}}
 @Test public void boundsVocabularySize(){try{words("a".repeat(2049));fail("Must reject oversized settings");}catch(IllegalArgumentException expected){}}
}
