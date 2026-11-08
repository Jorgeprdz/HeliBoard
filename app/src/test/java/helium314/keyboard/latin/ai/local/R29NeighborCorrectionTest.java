package helium314.keyboard.latin.ai.local;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Literal user regressions plus negative controls; synthetic dictionary, not accuracy claims. */
public class R29NeighborCorrectionTest {
 static class Lex implements LocalContextReviewer.Lexicon {
  final Set<String> words=new HashSet<>(Arrays.asList("mejora la detección de pulsar teclas vecinas hola como tengo que teclado funciona también canción rápido teléfono no ni me le si sí el él mas más tu tú continuo termino sunbathing banana".split(" ")));
  final Set<String> unknown=new HashSet<>(),protectedWords=new HashSet<>();
  public boolean valid(String word){return words.contains(word);}
  public boolean knownInvalid(String word){return !valid(word)&&!unknown.contains(word);}
  public boolean preserved(String word){return protectedWords.contains(word);}
  public List<String> candidates(String word){return Collections.emptyList();}
  public double keyDistance(char a,char b){return TypingCandidateRanker.QWERTY.distance(a,b);}
 }
 static List<LocalContextReviewer.Correction> review(String text,Lex lex,Set<String> reject,LocalContextReviewer.Cancellation stop){return new LocalContextReviewer().review(text,lex,reject,stop);}
 static String apply(String text,Lex lex){
  List<LocalContextReviewer.Correction> found=review(text,lex,Collections.emptySet(),()->false);StringBuilder out=new StringBuilder(text);
  for(int i=found.size()-1;i>=0;i--){LocalContextReviewer.Correction c=found.get(i);assertEquals(c.original,text.substring(c.start,c.end));if(c.automaticAllowed)out.replace(c.start,c.end,c.replacement);}
  return out.toString();
 }
 @Test public void exactUserCompoundTypoRecoversWithoutNativeCandidateSeed(){assertEquals("mejora la detección de pulsar teclas vecinas ",apply("mejora la detecxiomnde pulsar teclas vecinas ",new Lex()));}
 @Test public void nAndBReplaceSpaceOnlyWithVerifiedUniqueWords(){for(String letter:Arrays.asList("n","b")){assertEquals("hola como ",apply("hola"+letter+"como ",new Lex()));assertEquals("teclado funciona ",apply("teclado"+letter+"funciona ",new Lex()));assertEquals("tengo que ",apply("tengo"+letter+"que ",new Lex()));}}
 @Test public void missingAcuteAccentsRecoverWithoutObservedCandidates(){assertEquals("también canción rápido teléfono ",apply("tambien cancion rapido telefono ",new Lex()));}
 @Test public void adjacentSubstitutionsRecoverWithDictionaryEvidence(){assertEquals("detección ",apply("detecxiom ",new Lex()));}
 @Test public void existingNativeCandidateDoesNotSuppressVerifiedNeighborRepair(){
  Lex lex=new Lex(){public List<String> candidates(String word){return word.equals("detecxiom")?Collections.singletonList("detección"):Collections.emptyList();}};
  assertEquals("detección ",apply("detecxiom ",lex));
 }
 @Test public void validBilingualWordsPronounsAndNegationsStayExact(){String original="no ni me le si el mas tu continuo termino sunbathing banana ";assertEquals(original,apply(original,new Lex()));}
 @Test public void wholeWordPersonalProtectionAndUnknownStatusVetoRepair(){for(String source:Arrays.asList("holancomo","detecxiomnde","tambien")){Lex lex=new Lex();lex.words.add(source);assertEquals(source+" ",apply(source+" ",lex));lex.words.remove(source);lex.unknown.add(source);assertEquals(source+" ",apply(source+" ",lex));lex.unknown.clear();lex.protectedWords.add(source);assertEquals(source+" ",apply(source+" ",lex));}}
 @Test public void rejectedCompoundAndAccentsRemainUnchanged(){for(String[] pair:new String[][]{{"detecxiomnde","detección de"},{"tambien","también"},{"holabcomo","hola como"}}){List<LocalContextReviewer.Correction> found=review(pair[0]+" ",new Lex(),Collections.singleton(pair[0]+"→"+pair[1]),()->false);assertTrue(found.toString(),found.stream().noneMatch(c->c.automaticAllowed));}}
 @Test public void equallyValidAccentAlternativesMustAbstain(){Lex lex=new Lex();lex.words.addAll(Arrays.asList("médico","medicó"));assertEquals("medico ",apply("medico ",lex));}
 @Test public void nonNeighborKeyboardCannotInventCompoundRepair(){Lex lex=new Lex(){public double keyDistance(char a,char b){return a==b?0:Double.POSITIVE_INFINITY;}public boolean spaceNeighbor(char c){return false;}};assertEquals("detecxiomnde ",apply("detecxiomnde ",lex));}
 @Test public void cancellationDiscardsAllProposals(){AtomicInteger calls=new AtomicInteger();assertTrue(review("tambien detecxiomnde ",new Lex(),Collections.emptySet(),()->calls.incrementAndGet()>5).isEmpty());}
 @Test public void quotedAddressesNamesAndEmojiOffsetsStaySafe(){for(String source:Arrays.asList("`detecxiomnde`","https://host/detecxiomnde","detecxiomnde@example.org","Detecxiomnde"))assertEquals(source,apply(source,new Lex()));assertEquals("🙂 detección de ",apply("🙂 detecxiomnde ",new Lex()));}
 private static String[][] alphabetCases(){return new String[][]{
   {"a","camino","camino","qwsz"},{"b","trabajo","trabajo","ghvn"},
   {"c","cocina","cocina","dfxv"},{"d","dibujo","dibujo","ersfxc"},
   {"e","escuela","escuela","wrsd"},{"f","familia","familia","rtdgcv"},
   {"g","guitarra","guitarra","tyfhvb"},{"h","hombre","hombre","yugjbn"},
   {"i","invierno","invierno","uojk"},{"j","jardín","jardin","uihknm"},
   {"k","kilómetro","kilometro","iojlm"},{"l","ladrillo","ladrillo","opkñ"},
   {"m","mañana","mañana","jkn"},{"n","ventana","ventana","hjbm"},
   {"ñ","señora","señora","pl"},{"o","tomate","tomate","ipkl"},
   {"p","pelota","pelota","olñ"},{"q","queso","queso","wa"},
   {"r","regalo","regalo","etdf"},{"s","semana","semana","weadzx"},
   {"t","tijera","tijera","ryfg"},{"u","universo","universo","yihj"},
   {"v","ventana","ventana","fgcb"},{"w","webcam","webcam","qeas"},
   {"x","examen","examen","sdzc"},{"y","payaso","payaso","tugh"},
   {"z","zapato","zapato","asx"}
  };}
 @Test public void everySpanishLetterRecoversHorizontalAndDiagonalNeighborInWords(){
  String[][] cases=alphabetCases();
  Lex lex=new Lex();for(String[] c:cases)lex.words.add(c[1]);
  Set<String> covered=new HashSet<>();int substitutions=0;
  for(String[] c:cases){
   covered.add(c[0]);int at=c[2].indexOf(c[0]);assertTrue(at>=0);
   for(char neighbor:c[3].toCharArray()){
    String typo=c[2].substring(0,at)+neighbor+c[2].substring(at+1);
    assertEquals(c[0]+"←"+neighbor+": "+typo,c[1]+" ",apply(typo+" ",lex));substitutions++;
   }
   assertEquals(c[1]+" ",apply(c[1]+" ",lex));
  }
  assertEquals(27,covered.size());System.out.println("R29_ALPHABET_FIXTURE: letters="+covered.size()+" neighborSubstitutions="+substitutions);
 }
 @Test public void actualAlignedSpanishLayoutCoversEveryLetterAndCornerNeighbor(){
  // Independent centers measured from the installed Spanish 10/10/7-key layout.
  Map<Character,double[]> positions=new HashMap<>();
  String[] rows={"qwertyuiop","asdfghjklñ","zxcvbnm"};
  for(int row=0;row<rows.length;row++)for(int x=0;x<rows[row].length();x++)
   positions.put(rows[row].charAt(x),new double[]{(row==2?214:53)+x*107,row*125,107,114});
  positions.put(' ',new double[]{550,375,486,114});
  ObservedLexicon observed=new ObservedLexicon();observed.keyboard(positions);
  LocalContextReviewer.Lexicon geometry=observed.snapshot();
  AtomicInteger queries=new AtomicInteger();
  Lex lex=new Lex(){public boolean valid(String word){queries.incrementAndGet();return super.valid(word);}public double keyDistance(char a,char b){return geometry.keyDistance(a,b);}public boolean spaceNeighbor(char c){return geometry.spaceNeighbor(c);}};
  for(String[] c:alphabetCases())lex.words.add(c[1]);
  Map<Character,String> adjacent=new HashMap<>();
  for(String entry:("q:was w:qeasd e:wrsdf r:etdfg t:ryfgh y:tughj u:yihjk i:uojkl o:ipklñ p:olñ "+
    "a:qws s:qweadz d:wersfzx f:ertdgxc g:rtyfhcv h:tyugjvb j:yuihkbn k:uiojlnm l:iopkñm ñ:opl "+
    "z:sdx x:dfzc c:fgxv v:ghcb b:hjvn n:jkbm m:kln").split(" "))
   adjacent.put(entry.charAt(0),entry.substring(2));
  int count=0;
  for(String[] c:alphabetCases()){
   int at=c[2].indexOf(c[0]);
   for(char wrong:adjacent.get(c[0].charAt(0)).toCharArray()){
    String typo=c[2].substring(0,at)+wrong+c[2].substring(at+1);
    int before=queries.get();String actual=apply(typo+" ",lex);
    assertEquals("Actual layout "+c[0]+"←"+wrong+": "+typo+" dictionaryQueries="+(queries.get()-before),c[1]+" ",actual);count++;
   }
  }
  assertEquals(27,adjacent.size());System.out.println("R29_TACTIC_GEOMETRY_FIXTURE: letters=27 neighborSubstitutions="+count);
 }
 @Test public void existingAcuteAccentIsPreservedWhileRepairingAnotherKey(){Lex lex=new Lex();lex.words.add("lápiz");assertEquals("lápiz ",apply("kápiz ",lex));}
 @Test public void exhaustedVariantSearchCannotCommitAnEarlyCandidate(){
  AtomicInteger calls=new AtomicInteger();Lex lex=new Lex(){public boolean valid(String word){calls.incrementAndGet();return super.valid(word);}};
  String original="aeiouaeiouaeiouae ";assertEquals(original,apply(original,lex));assertTrue("work remains bounded",calls.get()<2500);
 }
}
