// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import java.text.Normalizer;
import java.util.*;

/** Bounded motor-error ranking. Scores are conservative heuristics, never probabilities.
 * Native candidate priors already include dictionary frequency and active-language evidence.
 * No dictionary, network, editor access or retained text here.
 */
public final class TypingCandidateRanker {
 public interface Geometry { double distance(char a,char b); }
 public static final Geometry QWERTY=(a,b)->{
  String[] rows={"qwertyuiop","asdfghjklñ","zxcvbnm"};
  double ax=-1,ay=-1,bx=-1,by=-1;
  for(int y=0;y<rows.length;y++){
   int x=rows[y].indexOf(a);if(x>=0){ax=x+y*.25;ay=y;}
   x=rows[y].indexOf(b);if(x>=0){bx=x+y*.25;by=y;}
  }
  return ax<0||bx<0?Double.POSITIVE_INFINITY:Math.hypot(ax-bx,ay-by);
 };
 private TypingCandidateRanker(){}
 private static String fold(String s){
  return Normalizer.normalize(s.replace('ñ',(char)1),Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace((char)1,'ñ');
 }
 public static double editCost(String source,String target,Geometry keys){
  if(source==null||target==null||source.length()>48||target.length()>48)return Double.POSITIVE_INFINITY;
  String a=fold(source),b=fold(target);if(Math.abs(a.length()-b.length())>3)return Double.POSITIVE_INFINITY;
  double[][] d=new double[a.length()+1][b.length()+1];
  for(int i=0;i<=a.length();i++)d[i][0]=i*.85;
  for(int j=0;j<=b.length();j++)d[0][j]=j*.85;
  for(int i=1;i<=a.length();i++)for(int j=1;j<=b.length();j++){
   char x=a.charAt(i-1),y=b.charAt(j-1);
   double replacement=x==y?0:keys.distance(x,y)<=1.3?.65:1.25;
   double deletion=i>1&&x==a.charAt(i-2)?.35:.85;
   double insertion=j>1&&y==b.charAt(j-2)?.35:.85;
   d[i][j]=Math.min(d[i-1][j]+deletion,Math.min(d[i][j-1]+insertion,d[i-1][j-1]+replacement));
   if(i>1&&j>1&&x==b.charAt(j-2)&&a.charAt(i-2)==y)d[i][j]=Math.min(d[i][j],d[i-2][j-2]+.55);
  }
  return d[a.length()][b.length()]+(source.equals(target)||!a.equals(b)?0:.12);
 }
 public static final class Choice {
  public final String word;public final double score,cost,margin;public final boolean automatic;
  Choice(String word,double score,double cost,double margin,boolean automatic){this.word=word;this.score=score;this.cost=cost;this.margin=margin;this.automatic=automatic;}
 }
 public static Choice choose(String typed,String previous,String next,LocalContextReviewer.Lexicon lexicon,LocalContextReviewer.Cancellation cancellation){
  List<String> candidates=lexicon.candidates(typed);if(candidates==null)return null;
  double best=-Double.MAX_VALUE,second=-Double.MAX_VALUE,bestCost=0;String word=null;int count=0;
  Set<String> seen=new HashSet<>();
  for(String candidate:candidates){
   if(++count>24||cancellation.cancelled())return null;
   if(candidate==null||!candidate.matches("[\\p{Ll}\\p{M}]{2,48}")||!seen.add(candidate)||candidate.equals(typed)
      ||Arrays.asList("no","not","never","nunca","jamás","sin","ni","nor","neither").contains(candidate)
      ||lexicon.rejected(typed,candidate)||!lexicon.valid(candidate))continue;
   double cost=editCost(typed,candidate,lexicon::keyDistance);if(cost>2.2)continue;
   double context=0;
   if("estado".equals(previous)&&Arrays.asList("mexico","méxico").contains(next)&&"de".equals(candidate))context=.22;
   if(Arrays.asList("el","un").contains(previous)&&candidate.endsWith("o"))context+=.04;
   if(Arrays.asList("la","una").contains(previous)&&candidate.endsWith("a"))context+=.04;
   double score=2-cost+Math.max(0,Math.min(.12,lexicon.prior(typed,candidate)))
      +Math.max(0,Math.min(.18,lexicon.personalPreference(typed,candidate)))+context;
   if(score>best){second=best;best=score;word=candidate;bestCost=cost;}else second=Math.max(second,score);
  }
  if(word==null||cancellation.cancelled())return null;
  double margin=best-second;
  boolean geographic=typed.length()==2&&"estado".equals(previous)&&Arrays.asList("mexico","méxico").contains(next)&&"de".equals(word);
  boolean automatic=lexicon.knownInvalid(typed)&&!lexicon.valid(typed)&&margin>=.30&&bestCost<=1.05
      &&(typed.length()>=5||geographic);
  return new Choice(word,best,bestCost,margin,automatic);
 }
}
