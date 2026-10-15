// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import java.util.*;
/** Bounded ephemeral native candidates and keyboard geometry; no persistent editor text. */
public final class ObservedLexicon {
 private static final class Entry {final boolean valid,complete;final List<String> candidates;Entry(boolean v,List<String> c,boolean checked){valid=v;complete=checked;candidates=Collections.unmodifiableList(c);}}
 private final LinkedHashMap<String,Entry> words=new LinkedHashMap<>();
 public void record(String word,boolean valid,List<String> candidates){record(word,valid,candidates,false);}
 public void record(String word,boolean valid,List<String> candidates,boolean complete){
  if(word==null||word.isEmpty()||word.length()>48||SensitiveInputPolicy.sensitiveText(word))return;
  List<String> safe=new ArrayList<>();if(candidates!=null)for(String candidate:candidates){if(candidate!=null&&candidate.length()<=48&&!SensitiveInputPolicy.sensitiveText(candidate))safe.add(candidate);if(safe.size()==24)break;}
  words.put(word.toLowerCase(Locale.ROOT),new Entry(valid,safe,complete));while(words.size()>256)words.remove(words.keySet().iterator().next());
 }
 private final Map<String,Map<String,Double>> priors=new LinkedHashMap<>();
 private Map<Character,double[]> keys=null;
 public void keyboard(Map<Character,double[]> positions){keys=new HashMap<>(positions);}
 public void nativePriors(String source,Map<String,Double> values){
  if(source==null||source.length()>48)return;
  priors.put(source.toLowerCase(Locale.ROOT),new HashMap<>(values));
  while(priors.size()>256)priors.remove(priors.keySet().iterator().next());
 }
 public LocalContextReviewer.Lexicon snapshot(){final Map<String,Entry> copy=new HashMap<>(words);
 final Map<String,Map<String,Double>> scores=new HashMap<>(priors);
 final Map<Character,double[]> positions=keys==null?null:new HashMap<>(keys);
 return new LocalContextReviewer.Lexicon(){
  public double prior(String source,String candidate){return scores.getOrDefault(source,Collections.emptyMap()).getOrDefault(candidate,0.0);}
  public double keyDistance(char a,char b){
   if(positions==null)return Double.POSITIVE_INFINITY;
   double[] x=positions.get(a),y=positions.get(b);if(x==null||y==null)return Double.POSITIVE_INFINITY;
   return Math.hypot(x[0]-y[0],x[1]-y[1])/Math.max(1,(x[2]+y[2])/2);
  }
  public boolean spaceNeighbor(char c){
   if(positions==null)return false;
   double[] key=positions.get(c),space=positions.get(' ');
   return key!=null&&space!=null&&key.length>=4&&space.length>=4
     &&Math.abs(key[0]-space[0])<=space[2]/2&&space[1]>key[1]
     &&space[1]-key[1]<=key[3]*1.6;
  }
  public boolean valid(String word){Entry e=copy.get(word.toLowerCase(Locale.ROOT));return e!=null&&e.valid;}
  public boolean knownInvalid(String word){Entry e=copy.get(word.toLowerCase(Locale.ROOT));return e!=null&&e.complete&&!e.valid;}
  public List<String> candidates(String word){Entry e=copy.get(word.toLowerCase(Locale.ROOT));return e==null?Collections.emptyList():e.candidates;}
 };}
 public void clear(){words.clear();priors.clear();keys=null;}
}
