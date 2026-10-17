// SPDX-License-Identifier: GPL-3.0-only
package tactic.experimental;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import helium314.keyboard.latin.ai.local.*;
public final class HybridWritingTest {
 static int checks;
 static final HybridCandidateGenerator.Lexicon LEX=new HybridCandidateGenerator.Lexicon(){
  final List<String> words=Arrays.asList("recibir","recbir","buenas","tardes","send","the","file","documento","we","he","was","were","ready","envio","envío","no","quiero","negocio","mañana","mañnaa","el","archivo","is","this","test");
  public Collection<String> vocabulary(){return words;}
  public boolean valid(String w){return words.contains(w)&&!Arrays.asList("recbir","mañnaa").contains(w);}
  public boolean knownInvalid(String w){return Arrays.asList("recbir","mañnaa","buenastardes","sendthefile","docu","mento").contains(w);}
  public boolean preserved(String w){return w.equals("archium");}
  public double frequency(String w){return 1;}
  public double contextual(String before,String value,String after){return before.endsWith("we ")&&value.equals("were")||before.isEmpty()&&value.equals("envío")&&after.equals(" el archivo")?1:0;}
 };
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static List<HybridCandidateGenerator.Edit> generate(String text,boolean baseline){
  if(baseline){
   LocalContextReviewer.Lexicon nativeLex=new LocalContextReviewer.Lexicon(){
    public boolean valid(String w){return LEX.valid(w);}
    public boolean knownInvalid(String w){return !valid(w);}
    public List<String> candidates(String w){return new ArrayList<>(LEX.vocabulary());}
   };
   List<HybridCandidateGenerator.Edit> old=new ArrayList<>();
   for(LocalContextReviewer.Correction e:new LocalContextReviewer().review(text,nativeLex,Collections.emptySet(),()->false))
    old.add(new HybridCandidateGenerator.Edit(e.start,e.end,e.replacement,0));
   return old;
  }
  return new HybridCandidateGenerator(LEX).generate(text,()->false);
 }
 static void has(String text,String source,String target,boolean baseline){
  check(generate(text,baseline).stream().anyMatch(e->text.substring(e.start,e.end).equals(source)&&e.replacement.equals(target)),source+" -> "+target);
 }
 static void validWordContract(){
  check(generate("we",false).isEmpty(),"valid word without context must not receive motor replacements");
  has("we recbir","recbir","recibir",false);
  check(generate("we recbir",false).stream().noneMatch(e->e.start==0),
   "valid word within mixed span must not receive motor replacements");
 }
 static void lexicalSafetyContract(){
  final Set<String> valid=new HashSet<>(Arrays.asList("foros","fotos","casa","cosa","este","esté","teclado","quiero","hola","no","archium","meet","amigos","el","archivo","envio","envío"));
  HybridCandidateGenerator.Lexicon complete=new HybridCandidateGenerator.Lexicon(){
   public Collection<String> vocabulary(){return valid;}
   public boolean valid(String w){return valid.contains(w);}
   public boolean knownInvalid(String w){return !valid(w);}
   public boolean preserved(String w){return w.equals("archium");}
  };
  HybridCandidateGenerator safe=new HybridCandidateGenerator(complete);
  for(String word:Arrays.asList("foros","casa","quiero")){
   check(safe.generate(word,()->false).stream().noneMatch(e->e.start==0),"valid source motor-stable: "+word);
  }
  check(safe.generate("este teclado",()->false).isEmpty(),"ambiguous accent lacks contextual evidence");
  for(String text:Arrays.asList("Ana teclao","no teclao","archium teclao","teclao 2026")){
   List<HybridCandidateGenerator.Edit> result=safe.generate(text,()->false);
   check(result.stream().anyMatch(e->text.substring(e.start,e.end).equals("teclao")&&e.replacement.equals("teclado")),
     "protected neighbor still correctable: "+text);
   check(result.stream().noneMatch(e->e.automatic),"all hybrid proposals remain manual: "+text);
  }
  HybridCandidateGenerator.Lexicon incomplete=new HybridCandidateGenerator.Lexicon(){
   public Collection<String> vocabulary(){return valid;}
   public boolean valid(String w){return valid.contains(w);}
  };
  check(new HybridCandidateGenerator(incomplete).generate("teclao",()->false).isEmpty(),"unknown lexical status abstains");
  LocalContextReviewer.Lexicon stale=new LocalContextReviewer.Lexicon(){
   public boolean valid(String w){return false;}
   public boolean knownInvalid(String w){return w.equals("foros");}
   public List<String> candidates(String w){return Collections.emptyList();}
  };
  java.util.concurrent.atomic.AtomicInteger lookedUp=new java.util.concurrent.atomic.AtomicInteger();
  BoundedDictionaryLexicon nativeDictionary=new BoundedDictionaryLexicon(stale,w->{lookedUp.incrementAndGet();return w.equals("foros");},()->false);
  check(nativeDictionary.valid("foros")&&nativeDictionary.valid("foros")&&lookedUp.get()==1,
    "native validity overrides stale invalid and caches one lookup");
  BoundedDictionaryLexicon unavailable=new BoundedDictionaryLexicon(stale,w->{throw new IllegalStateException("offline");},()->false);
  try {unavailable.knownInvalid("teclao");throw new AssertionError("Missing native data misread as invalid");}
  catch(IllegalStateException expected){checks++;}
 }
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("valid-word-regression")){validWordContract();return;}
  boolean baseline=args.length>0&&args[0].equals("red");
  has("recbir el archivo","recbir","recibir",baseline);
  has("buenastardes","buenastardes","buenas tardes",baseline);
  has("sendthefile","sendthefile","send the file",baseline);
  has("docu mento","docu mento","documento",baseline);
  has("we was ready","was","were",baseline);
  has("envio el archivo","envio","envío",baseline);
  check(generate("No quiero 3, quiero 2",false).isEmpty(),"numbers/negation remain untouched");
  check(generate("Revisa los PRs de Archium",false).isEmpty(),"unknown capitalized names remain untouched");
  check(generate("can't send the file",false).isEmpty(),"contractions cannot lose negation");
  has("😀 recbir el archivo","recbir","recibir",false);
  check(generate("echo token=$SECRET",false).isEmpty(),"commands are excluded");
  check(generate("mail@example.com",false).isEmpty(),"email excluded");
  check(generate("negocio mañana",false).stream().noneMatch(e->e.replacement.contains(" no ")),"never introduce negation");
  check(new HybridCandidateGenerator(LEX).generate("recbir",()->true).isEmpty(),"cancelled generation abstains");
  check(new HybridCandidateGenerator(LEX).generate("x".repeat(300),()->false).isEmpty(),"context bound");
  List<HybridCandidateGenerator.Edit> edits=generate("recbir el archivo",false);
  check(edits.size()<=24&&edits.stream().noneMatch(e->e.automatic),"uncalibrated edits never automatic");
  for(HybridCandidateGenerator.Edit e:edits)check(!e.replacement.equals(""),"no deletion proposals");
  // Use the production exact anchor against appended text, not a fabricated editor.
  Object editor=new Object();ContinuousReviewAnchor anchor=new ContinuousReviewAnchor(editor,"test",1,7,0,"we was ready ");
  check(anchor.matches(editor,"test",1,7,18,18,"we was ready now  "),"appended typing preserves source");
  check(!anchor.matches(editor,"test",1,7,18,18,"we are ready now  "),"edited dependency invalidates");
  try(ClauseReviewQueue queue=new ClauseReviewQueue()){
   CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(2);
   List<String> outputs=Collections.synchronizedList(new ArrayList<>());
   queue.offer("first",()->{started.countDown();release.await();return "first";},v->{outputs.add(v);done.countDown();});
   check(started.await(2,TimeUnit.SECONDS),"worker begins");
   queue.offer("middle",()->"middle",v->outputs.add(v));
   queue.offer("latest",()->"latest",v->{outputs.add(v);done.countDown();});
   release.countDown();check(done.await(2,TimeUnit.SECONDS),"worker and latest delivered");
   check(outputs.equals(Arrays.asList("first","latest")),"bounded queue replaces pending, not running review");
   CountDownLatch running=new CountDownLatch(1),unblock=new CountDownLatch(1);
   queue.offer("obsolete",()->{running.countDown();unblock.await();return "obsolete";},outputs::add);
   check(running.await(2,TimeUnit.SECONDS),"obsolete job starts");queue.invalidate();unblock.countDown();
   queue.close();check(!outputs.contains("obsolete"),"invalidated result never delivered");
  }
  validWordContract();lexicalSafetyContract();
  System.out.println("Hybrid component assertions PASS: "+checks);
 }
}
