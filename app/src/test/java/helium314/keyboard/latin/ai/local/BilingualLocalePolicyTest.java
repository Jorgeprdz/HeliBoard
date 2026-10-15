// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class BilingualLocalePolicyTest {
 private List<Locale> apply(List<Locale> locales,boolean enabled){try{return (List<Locale>)Class.forName("helium314.keyboard.latin.ai.local.BilingualLocalePolicy").getMethod("locales",List.class,boolean.class).invoke(null,locales,enabled);}catch(Exception e){throw new AssertionError("Bilingual dictionary policy missing",e);}}
 @Test public void optOutPreservesConfiguredLanguages(){List<Locale> input=Arrays.asList(new Locale("es","MX"));assertEquals(input,apply(input,false));}
 @Test public void optInRetainsPrimaryLayoutLanguageAndAddsEnglish(){Locale primary=new Locale("es","MX");List<Locale> output=apply(Arrays.asList(primary),true);assertEquals(primary,output.get(0));assertTrue(output.contains(Locale.US));}
 @Test public void englishGetsMexicanSpanishWithoutDuplicateDictionaries(){List<Locale> output=apply(Arrays.asList(Locale.US,new Locale("es","MX")),true);assertEquals(2,output.size());assertEquals(Locale.US,output.get(0));}
}
