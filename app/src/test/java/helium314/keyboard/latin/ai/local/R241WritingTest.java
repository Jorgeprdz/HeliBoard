// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;import org.junit.runner.RunWith;import org.junit.runners.Parameterized;
import java.util.*;import static org.junit.Assert.*;
/** Dictionary fixture validates real correction outcomes, never substitutes for a language model. */
@RunWith(Parameterized.class)
public class R241WritingTest {
 @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases(){return Arrays.asList(new Object[][]{
  {"Edta es una prueba ","Esta es una prueba "},
  {"Edta trabaja ","Edta trabaja "},
  {"poryectos ","proyectos "},{"funcoona ","funciona "},
  {"nincuidando ","ni cuidando "},{"nicuidando ","ni cuidando "},{"sincuidar ","sin cuidar "},
  {"estoybescribiendonde ","estoy escribiendo de "},{"estoynescribiendobde ","estoy escribiendo de "},
  {"estoyescribiendo ","estoy escribiendo "},
  {"tener mad coherencia ","tener más coherencia "},{"necesito mad paciencia ","necesito más paciencia "},
  {"mad developer ","mad developer "},{"No quiero 3, quiero 2","No quiero 3, quiero 2"},
  {"yo envió un mensaje ","yo envío un mensaje "},{"suelo hacer cuando envió un mensaje ","suelo hacer cuando envío un mensaje "},
  {"ella envió un mensaje ","ella envió un mensaje "},{"envió un mensaje ","envió un mensaje "},
  {"mensaje .","mensaje."},{"reporte , ","reporte, "},{"mensaje ...","mensaje..."},
  {"la la gramática ","la gramática "},{"el el proyecto ","el proyecto "},
  {"muy muy bien ","muy muy bien "},{"no no quiero ","no no quiero "},{"la la la ","la la la "},
  {"escritura ay validación ","escritura y validación "},{"ay, validación ","ay, validación "},
  {"Estado dw Mexico ","Estado de Mexico "},{"Estado de México","Estado de México"},{"DW","DW"},
  {"Revisa los PRs de Archium","Revisa los PRs de Archium"},
  {"https://host/nincuidando","https://host/nincuidando"},{"nincuidando@example.org","nincuidando@example.org"},
  {"`nincuidando mensaje .`","`nincuidando mensaje .`"},
  {"🙂 nincuidando ","🙂 ni cuidando "}
 });}
 private final String input,expected;
 public R241WritingTest(String input,String expected){this.input=input;this.expected=expected;}
 public static class Vocabulary implements LocalContextReviewer.Lexicon {
  private final Set<String> valid=new HashSet<>(Arrays.asList(("esta es una prueba proyectos funciona ni no cuidando cuidar sin estoy escribiendo de manera rápida yo envió envío un mensaje cuando suelo hacer "+
   "ella tener tengo necesito más mad coherencia paciencia developer reporte la el los las gramática proyecto muy bien "+
   "escritura ay y validación Estado estado Mexico mexico México méxico en teoría acentos también debería orden particular seguir manera escribir como lo hacer "+
   "solo demás uno dos código fuente").split(" ")));
  public boolean valid(String w){return valid.contains(w);}
  public boolean knownInvalid(String w){return !valid(w);}
  public List<String> candidates(String w){
   switch(w.toLowerCase(Locale.ROOT)){
    case "edta":return Arrays.asList("esta");case "poryectos":return Arrays.asList("proyectos");
    case "funcoona":return Arrays.asList("funciona");case "dw":return Arrays.asList("de");
    case "slll":return Arrays.asList("solo");default:return Collections.emptyList();
   }
  }
 }
 public static String apply(String text,Set<String> rejected){
  List<LocalContextReviewer.Correction> changes=new LocalContextReviewer().review(text,new Vocabulary(),rejected,()->false);
  StringBuilder result=new StringBuilder(text);
  for(int i=changes.size()-1;i>=0;i--){LocalContextReviewer.Correction c=changes.get(i);if(c.automaticAllowed)result.replace(c.start,c.end,c.replacement);}
  return result.toString();
 }
 @Test public void realOutcomeIsConservativeAndDeterministic(){assertEquals(expected,apply(input,Collections.emptySet()));}
}
