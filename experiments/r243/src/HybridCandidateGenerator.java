// SPDX-License-Identifier: GPL-3.0-only
package tactic.experimental;
import java.util.*;import java.util.regex.*;
import helium314.keyboard.latin.ai.local.*;
/** Host prototype: N-best span edits. No Android hooks and no calibrated auto-apply policy. */
public final class HybridCandidateGenerator {
 public interface Lexicon extends LocalContextReviewer.Lexicon {
  Collection<String> vocabulary();
  default List<String> candidates(String word){return Collections.emptyList();}
  default double frequency(String word){return 0;}
  default double contextual(String before,String replacement,String after){return 0;}
 }
 public static final class Edit {
  public final int start,end;public final String replacement;public final double score;
  public final boolean automatic=false;
  public Edit(int start,int end,String replacement,double score){this.start=start;this.end=end;this.replacement=replacement;this.score=score;}
 }
 private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{M}]+");
 private static final Set<String> NEG=new HashSet<>(Arrays.asList("no","ni","sin","nunca","jamás","tampoco","not","never","neither","nor","don't","can't","won't","will","shall","must","prometo","prometí","pagaré","haré","acepto","acepté"));
 private final Lexicon lex;
 private final Map<Integer,List<String>> lengths=new HashMap<>();
 public HybridCandidateGenerator(Lexicon lex){
  this.lex=Objects.requireNonNull(lex);
  if(lex.vocabulary().size()>50000)throw new IllegalArgumentException("Vocabulary exceeds prototype bound");
  for(String w:lex.vocabulary())if(w!=null&&w.matches("[\\p{Ll}\\p{M}]{1,32}")&&lex.valid(w)&&!lex.preserved(w)&&!NEG.contains(w))
   lengths.computeIfAbsent(w.length(),k->new ArrayList<>()).add(w);
  for(List<String> words:lengths.values()){words.sort(Comparator.<String>comparingDouble(lex::frequency).reversed().thenComparing(Comparator.naturalOrder()));}
 }
 private static String accentFamily(String text){
  return java.text.Normalizer.normalize(text.replace('ñ',(char)1),java.text.Normalizer.Form.NFD)
   .replaceAll("\\p{M}+","").replace((char)1,'ñ');
 }
 private static final class Token {int start,end;String word;Token(int s,int e,String w){start=s;end=e;word=w;}}
 private static final class State {int pos;String text;double cost;State(int p,String t,double c){pos=p;text=t;cost=c;}}
 public List<Edit> generate(String context,LocalContextReviewer.Cancellation cancel){
  if(context==null||context.length()>256||cancel.cancelled()||context.indexOf(39)>=0||context.indexOf(8217)>=0||helium314.keyboard.latin.ai.SensitiveInputPolicy.sensitiveText(context)||context.matches("(?s).*[@/\\\\\\x60_=<>\\p{N}].*"))return Collections.emptyList();
  List<Token> tokens=new ArrayList<>();Matcher m=WORD.matcher(context);
  while(m.find()){
   String w=m.group();if(!w.equals(w.toLowerCase(Locale.ROOT))||lex.preserved(w)||NEG.contains(w))return Collections.emptyList();
   tokens.add(new Token(m.start(),m.end(),w));
  }
  if(tokens.size()>32)return Collections.emptyList();
  Map<String,Edit> unique=new HashMap<>(),contextual=new HashMap<>();int[] budget={4096};
  for(int width=1;width<=3;width++)for(int first=0;first+width<=tokens.size();first++){
   if(cancel.cancelled())return Collections.emptyList();if(budget[0]<=0)break;
   Token left=tokens.get(first),right=tokens.get(first+width-1);
   String source=context.substring(left.start,right.end);
   if(!source.matches("[\\p{Ll}\\p{M}]+(?:[ \\t]+[\\p{Ll}\\p{M}]+){0,2}"))continue;
   String flat=source.replaceAll("[ \\t]+","");if(flat.length()>48)continue;
   boolean allValid=true;for(int index=first;index<first+width;index++)allValid&=lex.valid(tokens.get(index).word);
   if(allValid){
    // A legitimate source is not a motor-error target. Consider only accent alternatives
    // or alternatives supported by external contextual evidence; never automatic.
    if(width==1)for(int size=Math.max(1,source.length()-2);size<=Math.min(32,source.length()+2);size++){
     List<String> options=lengths.getOrDefault(size,Collections.emptyList());
     for(int n=0;n<Math.min(64,options.size());n++){
      if(cancel.cancelled())return Collections.emptyList();
      String target=options.get(n);if(target.equals(source)||lex.rejected(source,target))continue;
      double evidence=lex.contextual(context.substring(0,left.start),target,context.substring(right.end));
      String key=left.start+":"+right.end+":"+target;
      if(Double.isFinite(evidence)&&evidence>0)contextual.put(key,new Edit(left.start,right.end,target,-evidence));
      else if(accentFamily(source).equals(accentFamily(target)))
       unique.put(key,new Edit(left.start,right.end,target,TypingCandidateRanker.editCost(source,target,lex::keyDistance)));
     }
    }
    continue;
   }
   List<State> beam=new ArrayList<>();beam.add(new State(0,"",0));
   for(int depth=0;depth<3&&!beam.isEmpty();depth++){
    List<State> next=new ArrayList<>();
    for(State state:beam){
     int remaining=flat.length()-state.pos;
     for(int take=Math.max(1,remaining-(2-depth)*32);take<=Math.min(32,remaining);take++){
      String fragment=flat.substring(state.pos,state.pos+take);
      for(int size=Math.max(1,take-2);size<=Math.min(32,take+2);size++){
       List<String> options=lengths.getOrDefault(size,Collections.emptyList());
       for(int n=0;n<Math.min(64,options.size());n++){
        if(cancel.cancelled())return Collections.emptyList();if(--budget[0]<0)break;
        String word=options.get(n);
        double cost=state.cost+TypingCandidateRanker.editCost(fragment,word,lex::keyDistance);
        if(cost>2.8)continue;
        String replacement=state.text.isEmpty()?word:state.text+" "+word;int end=state.pos+take;
        if(end==flat.length()){
         if(replacement.equals(source)||lex.rejected(source,replacement))continue;
         double score=cost+.12*Math.abs(depth+1-width)
          -.10*Math.max(0,Math.min(1,lex.frequency(word)))
          -.30*Math.max(0,Math.min(1,lex.contextual(context.substring(0,left.start),replacement,context.substring(right.end))))
          -.18*Math.max(0,Math.min(1,lex.personalPreference(source,replacement)));
         Edit edit=new Edit(left.start,right.end,replacement,score);
         String key=left.start+":"+right.end+":"+replacement;Edit previous=unique.get(key);
         if(previous==null||score<previous.score)unique.put(key,edit);
        }else if(depth<2)next.add(new State(end,replacement,cost));
       }
       if(budget[0]<=0)break;
      }
      if(budget[0]<=0)break;
     }
    }
    next.sort(Comparator.comparingDouble((State v)->v.cost).thenComparing(v->v.text));
    beam=next.subList(0,Math.min(12,next.size()));
   }
  }
  List<Edit> out=new ArrayList<>(unique.values());
  out.sort(Comparator.comparingDouble((Edit e)->e.score).thenComparingInt(e->e.start).thenComparing(e->e.replacement));
  List<Edit> result=new ArrayList<>(out.subList(0,Math.min(16,out.size())));
  List<Edit> grammar=new ArrayList<>(contextual.values());
  grammar.sort(Comparator.comparingDouble((Edit e)->e.score).thenComparing(e->e.replacement));
  for(Edit e:grammar){
   if(result.size()==24)break;
   boolean duplicate=result.stream().anyMatch(v->v.start==e.start&&v.end==e.end&&v.replacement.equals(e.replacement));
   if(!duplicate)result.add(e);
  }
  for(Edit e:out){
   if(result.size()==24)break;
   if(!result.contains(e))result.add(e);
  }
  return Collections.unmodifiableList(result);
 }
}
