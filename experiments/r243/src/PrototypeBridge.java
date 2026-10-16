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
   public Collection<String> vocabulary(){List<String> out=new ArrayList<>();for(String w:words.keySet())if(!w.contains(" "))out.add(w);return out;}
   public boolean valid(String w){return words.containsKey(w);}
   public boolean knownInvalid(String w){return !valid(w);}
   public double frequency(String w){return Math.min(1,Math.log1p(words.getOrDefault(w,0.0))/20);}
   public double contextual(String before,String value,String after){
    String[] left=before.trim().split("\\s+"),right=after.trim().split("\\s+");
    String previous=left.length==0?"":left[left.length-1],next=right.length==0?"":right[0];
    return Math.min(1,Math.log1p(words.getOrDefault(previous+" "+value,0.0)+words.getOrDefault(value+" "+next,0.0))/20);
   }
   public List<String> candidates(String w){
    List<String> candidates=new ArrayList<>(vocabulary());
    Map<String,Double> costs=new HashMap<>();
    candidates.sort(Comparator.comparingDouble(c->costs.computeIfAbsent(c,key->TypingCandidateRanker.editCost(w,key,TypingCandidateRanker.QWERTY))));
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
  if(args[1].equals("legacy-all")){
   LocalContextReviewer.Correction suggestion=null;
   for(LocalContextReviewer.Correction c:corrections)if(!c.automaticAllowed){suggestion=c;break;}
   StringBuilder assisted=new StringBuilder(input);
   for(int i=corrections.size()-1;i>=0;i--){LocalContextReviewer.Correction c=corrections.get(i);if(c.automaticAllowed||c==suggestion)assisted.replace(c.start,c.end,c.replacement);}
   System.out.println("assisted\t"+enc(assisted.toString()));
  }
 }
}
