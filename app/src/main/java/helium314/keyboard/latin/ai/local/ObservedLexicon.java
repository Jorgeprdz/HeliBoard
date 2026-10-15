// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import java.util.*;
/** Bounded ephemeral dictionary observations. The reviewer never calls native dictionaries. */
public final class ObservedLexicon {
 private static final class Entry {final boolean valid;final List<String> candidates;Entry(boolean v,List<String> c){valid=v;candidates=Collections.unmodifiableList(c);}}
 private final LinkedHashMap<String,Entry> words=new LinkedHashMap<>();
 public void record(String word,boolean valid,List<String> candidates){
  if(word==null||word.isEmpty()||word.length()>48||SensitiveInputPolicy.sensitiveText(word))return;
  List<String> safe=new ArrayList<>();if(candidates!=null)for(String candidate:candidates){if(candidate!=null&&candidate.length()<=48&&!SensitiveInputPolicy.sensitiveText(candidate))safe.add(candidate);if(safe.size()==24)break;}
  words.put(word.toLowerCase(Locale.ROOT),new Entry(valid,safe));while(words.size()>256)words.remove(words.keySet().iterator().next());
 }
 public LocalContextReviewer.Lexicon snapshot(){final Map<String,Entry> copy=new HashMap<>(words);return new LocalContextReviewer.Lexicon(){
  public boolean valid(String word){Entry e=copy.get(word.toLowerCase(Locale.ROOT));return e!=null&&e.valid;}
  public List<String> candidates(String word){Entry e=copy.get(word.toLowerCase(Locale.ROOT));return e==null?Collections.emptyList():e.candidates;}
 };}
 public void clear(){words.clear();}
}
