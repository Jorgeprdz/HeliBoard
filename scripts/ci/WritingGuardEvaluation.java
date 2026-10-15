// SPDX-License-Identifier: GPL-3.0-only
import helium314.keyboard.latin.ai.local.*;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Linux component comparison with an explicit dictionary fixture, not an Android IME claim. */
public final class WritingGuardEvaluation {
 public static void main(String[] args)throws Exception{
  Set<String> dictionary=new HashSet<>(Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8));
  String source=Files.readString(Paths.get(args[1])),generated=Files.readString(Paths.get(args[2]));
  LocalContextReviewer.Lexicon lexicon=new LocalContextReviewer.Lexicon(){
   public boolean valid(String w){return dictionary.contains(w.toLowerCase(Locale.ROOT));}
   public boolean knownInvalid(String w){return !valid(w);}
   public List<String> candidates(String w){
    List<String> words=new ArrayList<>(dictionary);
    words.sort(Comparator.comparingDouble(c->TypingCandidateRanker.editCost(w,c,TypingCandidateRanker.QWERTY)));
    return words.subList(0,Math.min(24,words.size()));
   }
  };
  List<LocalContextReviewer.Correction> baseline=new LocalContextReviewer().review(source,lexicon,Collections.emptySet(),()->false);
  List<LocalContextReviewer.Correction> proposals=SemanticEditGuard.proposals(source,generated,lexicon,Collections.emptySet(),()->false);
  StringBuilder old=new StringBuilder(source),accepted=new StringBuilder(source);
  for(int i=baseline.size()-1;i>=0;i--){LocalContextReviewer.Correction edit=baseline.get(i);if(edit.automaticAllowed)old.replace(edit.start,edit.end,edit.replacement);}
  for(int i=proposals.size()-1;i>=0;i--){LocalContextReviewer.Correction edit=proposals.get(i);if(edit.automaticAllowed)throw new AssertionError("Model auto-apply forbidden");accepted.replace(edit.start,edit.end,edit.replacement);}
  Files.writeString(Paths.get(args[3]+".baseline"),old);Files.writeString(Paths.get(args[3]+".accepted"),accepted);
  Files.writeString(Paths.get(args[3]+".count"),Integer.toString(proposals.size()));
  if(args.length>4&&args[4].equals("prepare")){
   StringBuilder targets=new StringBuilder();
   for(CorrectionFocus focus:CorrectionFocus.targets(old.toString(),lexicon,()->false)){
    String prompt=CorrectionFocus.prompt(old.toString(),focus.original,"original");
    targets.append(focus.start).append("\t").append(focus.end).append("\t")
     .append(Base64.getEncoder().encodeToString(focus.original.getBytes(StandardCharsets.UTF_8))).append("\t")
     .append(Base64.getEncoder().encodeToString(prompt.getBytes(StandardCharsets.UTF_8))).append("\n");
   }
   Files.writeString(Paths.get(args[3]+".focus"),targets);
  }
 }
}
