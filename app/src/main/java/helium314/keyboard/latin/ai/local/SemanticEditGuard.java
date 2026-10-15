// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;import java.util.regex.*;
/** A model is untrusted. Produce bounded, disjoint, explicit-acceptance edits, never a message replacement. */
public final class SemanticEditGuard {
 private static final Pattern TOKEN=Pattern.compile("[\\p{L}\\p{M}\\p{N}_'’-]+|[^\\p{L}\\p{M}\\p{N}\\s]+");
 private static final Set<String> NEG=new HashSet<>(Arrays.asList("no","ni","sin","nunca","jamás","tampoco","not","never","neither","nor","don't","can't","won't"));
 private static final Set<String> COMMIT=new HashSet<>(Arrays.asList("prometo","prometí","pagaré","haré","acepto","acepté","will","shall","must"));
 private static final Set<String> FUNCTION=new HashSet<>(Arrays.asList("el","la","los","las","un","una","unos","unas","de","del","a","al","en","con","por","para","que","y","e","o","u","the","a","an","of","to","in","at","with"));
 private SemanticEditGuard(){}
 private static final class Piece {final int start,end;final String text;Piece(int a,int b,String t){start=a;end=b;text=t;}}
 private static List<Piece> pieces(String text){List<Piece> out=new ArrayList<>();Matcher m=TOKEN.matcher(text);while(m.find())out.add(new Piece(m.start(),m.end(),m.group()));return out;}
 private static boolean wordToken(String text){return !text.isEmpty()&&Character.isLetter(text.codePointAt(0));}
 private static boolean letters(String text){return text.matches("[\\p{L}\\p{M}]+");}
 private static List<String> protectedTerms(String text,LocalContextReviewer.Lexicon lex){
  List<String> out=new ArrayList<>();Matcher m=Pattern.compile("[\\p{L}\\p{M}\\p{N}_'’-]+").matcher(text);
  while(m.find()){
   String word=m.group(),lower=word.toLowerCase(Locale.ROOT);
   if(NEG.contains(lower)||COMMIT.contains(lower)||word.matches(".*[\\p{N}_].*")
       ||lex.preserved(lower))
    out.add(lower);
  }return out;
 }
 public static List<LocalContextReviewer.Correction> proposals(String original,String generated,LocalContextReviewer.Lexicon lex,
   Set<String> rejected,LocalContextReviewer.Cancellation cancel){
  if(original==null||generated==null||original.isEmpty()||original.length()>512||generated.length()>640
    ||cancel.cancelled()||original.equals(generated)||original.matches("(?s).*[@/\\\\`_=<>].*")
    ||!protectedTerms(original,lex).equals(protectedTerms(generated,lex)))return Collections.emptyList();
  List<Piece> a=pieces(original),b=pieces(generated);if(a.size()>192||b.size()>224)return Collections.emptyList();
  int[][] lcs=new int[a.size()+1][b.size()+1];
  for(int i=a.size()-1;i>=0;i--)for(int j=b.size()-1;j>=0;j--)
   lcs[i][j]=a.get(i).text.equals(b.get(j).text)?1+lcs[i+1][j+1]:Math.max(lcs[i+1][j],lcs[i][j+1]);
  List<LocalContextReviewer.Correction> result=new ArrayList<>();
  int i=0,j=0;
  while(i<a.size()||j<b.size()){
   if(cancel.cancelled())return Collections.emptyList();
   if(i<a.size()&&j<b.size()&&a.get(i).text.equals(b.get(j).text)){i++;j++;continue;}
   int ai=i,bj=j;
   while(i<a.size()||j<b.size()){
    if(i<a.size()&&j<b.size()&&a.get(i).text.equals(b.get(j).text))break;
    if(j<b.size()&&(i==a.size()||lcs[i][j+1]>=lcs[i+1][j]))j++;else i++;
   }
   int start=ai<a.size()?a.get(ai).start:original.length(),end=i>ai?a.get(i-1).end:start;
   int targetStart=bj<b.size()?b.get(bj).start:generated.length(),targetEnd=j>bj?b.get(j-1).end:targetStart;
   String source=original.substring(start,end),target=generated.substring(targetStart,targetEnd);
   if(source.isEmpty()){
    // Pure punctuation insertion uses a real left-hand word anchor. New lexical content is forbidden.
    if(target.matches(".*[\\p{L}\\p{N}].*")||ai==0)continue;
    Piece previous=a.get(ai-1);if(!letters(previous.text))continue;
    start=previous.start;source=original.substring(start,end);target=source+target;
   }
   if(source.length()>48||target.isEmpty()||target.length()>64||source.trim().isEmpty())continue;
   String x=source.replaceAll("[^\\p{L}\\p{M}]","").toLowerCase(Locale.ROOT);
   String y=target.replaceAll("[^\\p{L}\\p{M}]","").toLowerCase(Locale.ROOT);
   boolean protectedNameChanged=false;
   Set<String> oldWords=new HashSet<>(),newWords=new HashSet<>(),exactNewWords=new HashSet<>();
   for(Piece part:pieces(source))if(wordToken(part.text))oldWords.add(part.text.toLowerCase(Locale.ROOT));
   for(Piece part:pieces(target))if(wordToken(part.text)){newWords.add(part.text.toLowerCase(Locale.ROOT));exactNewWords.add(part.text);}
   for(Piece part:pieces(source))if(wordToken(part.text)&&!part.text.equals(part.text.toLowerCase(Locale.ROOT))
      &&!exactNewWords.contains(part.text))protectedNameChanged=true;
   for(Piece part:pieces(target))if(wordToken(part.text)&&!part.text.equals(part.text.toLowerCase(Locale.ROOT))
      &&!oldWords.contains(part.text.toLowerCase(Locale.ROOT)))protectedNameChanged=true;
   if(protectedNameChanged)continue;
   boolean allSourceValid=true,allFunction=true,validTarget=true;int targetWords=0;
   for(Piece part:pieces(source))if(letters(part.text)){
    String w=part.text.toLowerCase(Locale.ROOT);allSourceValid&=lex.valid(w);allFunction&=FUNCTION.contains(w);
   }
   for(Piece part:pieces(target))if(letters(part.text)){
    targetWords++;String w=part.text.toLowerCase(Locale.ROOT);
    validTarget&=lex.valid(w);allFunction&=FUNCTION.contains(w);
   }
   if(!validTarget||targetWords>6||x.isEmpty()||y.isEmpty())continue;
   double cost=TypingCandidateRanker.editCost(x,y,lex::keyDistance);
   if(cost>Math.max(2.8,x.length()*.4))continue;
   if(allSourceValid&&!allFunction&&!java.text.Normalizer.normalize(x,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")
      .equals(java.text.Normalizer.normalize(y,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")))continue;
   boolean veto=lex.rejected(source,target);if(rejected!=null)for(String r:rejected)
    veto|=r.equalsIgnoreCase(source)||r.equalsIgnoreCase(target)||r.equalsIgnoreCase(source+"→"+target);
   if(veto||(!result.isEmpty()&&result.get(result.size()-1).end>start))continue;
   result.add(new LocalContextReviewer.Correction(start,end,source,target,"local-model-explicit-choice",0,false));
   if(result.size()==6)break;
  }
  return cancel.cancelled()?Collections.emptyList():Collections.unmodifiableList(result);
 }
}
