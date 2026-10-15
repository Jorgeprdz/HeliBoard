// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;
/** Bounded dictionary segmentation with optional boundary motor noise. No phrase inventory. */
public final class WordBoundarySegmenter {
 private WordBoundarySegmenter(){}
 public static final class Choice {
  public final String text; public final boolean automatic;
  Choice(String text,boolean automatic){this.text=text;this.automatic=automatic;}
 }
 public static Choice choose(String word,LocalContextReviewer.Lexicon lexicon,LocalContextReviewer.Cancellation cancel){
  if(word.length()<6||word.length()>32||lexicon.valid(word)||!lexicon.knownInvalid(word))return null;
  Map<String,Double> found=new HashMap<>();
  int[] budget={0};
  search(word,0,new ArrayList<>(),0,lexicon,cancel,found,budget);
  if(budget[0]>256)return null;
  if(cancel.cancelled()||found.isEmpty())return null;
  List<Map.Entry<String,Double>> ordered=new ArrayList<>(found.entrySet());
  ordered.sort(Comparator.comparingDouble(Map.Entry::getValue));
  Map.Entry<String,Double> best=ordered.get(0);
  double margin=ordered.size()<2?Double.POSITIVE_INFINITY:ordered.get(1).getValue()-best.getValue();
  if(margin<.35||lexicon.rejected(word,best.getKey()))return null;
  return new Choice(best.getKey(),best.getValue()<=1.4&&licensed(best.getKey().split(" ")));
 }
 private static void search(String word,int pos,List<String> parts,double cost,LocalContextReviewer.Lexicon lexicon,
   LocalContextReviewer.Cancellation cancel,Map<String,Double> found,int[] budget){
  if(cancel.cancelled()||++budget[0]>256)return;
  if(pos==word.length()){
   if(parts.size()>=2){String value=String.join(" ",parts);Double old=found.get(value);if(old==null||old>cost)found.put(value,cost);}return;
  }
  if(parts.size()==3)return;
  for(int end=pos+2;end<=word.length();end++){
   if(cancel.cancelled())return;
   String part=word.substring(pos,end);
   if(!lexicon.valid(part)||lexicon.preserved(part))continue;
   parts.add(part);search(word,end,parts,cost,lexicon,cancel,found,budget);
   if(end+2<word.length()&&cost<=.75){
    char noise=word.charAt(end);
    boolean repeated=part.substring(Math.max(0,part.length()-2)).indexOf(noise)>=0;
    if(repeated||lexicon.spaceNeighbor(noise))
     search(word,end+1,parts,cost+(repeated?.35:.65),lexicon,cancel,found,budget);
   }
   parts.remove(parts.size()-1);
  }
 }
 private static boolean licensed(String[] p){
  String first=p[0],verb=p[1];
  boolean gerund=verb.endsWith("ando")||verb.endsWith("iendo");
  boolean infinitive=verb.endsWith("ar")||verb.endsWith("er")||verb.endsWith("ir");
  if(p.length==2&&Arrays.asList("ni","no","sin","para","por","de").contains(first))return gerund||infinitive;
  return Arrays.asList("estoy","estás","está","estamos","están","estaba","estabas","estaban").contains(first)&&gerund
   &&(p.length==2||Arrays.asList("de","con","por","para","en").contains(p[2]));
 }
}
