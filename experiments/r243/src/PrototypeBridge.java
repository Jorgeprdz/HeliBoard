// SPDX-License-Identifier: GPL-3.0-only
package tactic.experimental;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
import helium314.keyboard.latin.ai.local.*;
/** Line protocol: only external vocabulary and input context enter this process, never gold references. */
public final class PrototypeBridge {
 static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception{
  Map<String,Double> words=new LinkedHashMap<>();
  for(String line:Files.readAllLines(Path.of(args[0]))){
   if(line.isBlank()||line.startsWith("#"))continue;
   String[] fields=line.split("\\t");if(fields.length!=2)throw new IllegalArgumentException("Expected word TAB frequency");
   double weight=Double.parseDouble(fields[1]);if(!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("Frequency");
   words.put(fields[0],weight);
  }
  HybridCandidateGenerator.Lexicon lex=new HybridCandidateGenerator.Lexicon(){
   public Collection<String> vocabulary(){return words.keySet();}
   public boolean valid(String w){return words.containsKey(w);}
   public boolean knownInvalid(String w){return !valid(w);}
   public double frequency(String w){return Math.min(1,Math.log1p(words.getOrDefault(w,0.0))/20);}
   public List<String> candidates(String w){
    List<String> candidates=new ArrayList<>(words.keySet());
    candidates.sort(Comparator.comparingDouble(c->TypingCandidateRanker.editCost(w,c,TypingCandidateRanker.QWERTY)));
    return candidates.subList(0,Math.min(24,candidates.size()));
   }
  };
  String input=new String(Base64.getDecoder().decode(args[2]),StandardCharsets.UTF_8);
  if(args[1].equals("guard")){
   String result=new String(Base64.getDecoder().decode(args[3]),StandardCharsets.UTF_8);
   for(LocalContextReviewer.Correction c:SemanticEditGuard.proposals(input,result,lex,Collections.emptySet(),()->false))
    System.out.println("edit\t"+c.start+"\t"+c.end+"\t"+enc(c.replacement));
   return;
  }
  if(args[1].equals("hybrid")){
   for(HybridCandidateGenerator.Edit c:new HybridCandidateGenerator(lex).generate(input,()->false))
    System.out.println("edit\t"+c.start+"\t"+c.end+"\t"+enc(c.replacement));
   return;
  }
  if(args[1].equals("focus")){
   for(CorrectionFocus f:CorrectionFocus.targets(input,lex,()->false))
    System.out.println("focus\t"+f.start+"\t"+f.end+"\t"+enc(CorrectionFocus.prompt(input,f.original,"original")));
   return;
  }
  StringBuilder auto=new StringBuilder(input);
  List<LocalContextReviewer.Correction> corrections=new LocalContextReviewer().review(input,lex,Collections.emptySet(),()->false);
  for(int i=corrections.size()-1;i>=0;i--){LocalContextReviewer.Correction c=corrections.get(i);if(c.automaticAllowed)auto.replace(c.start,c.end,c.replacement);}
  System.out.println("automatic\t"+enc(auto.toString()));
 }
}
