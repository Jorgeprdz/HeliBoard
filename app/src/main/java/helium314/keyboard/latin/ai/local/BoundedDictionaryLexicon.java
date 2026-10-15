// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;
import java.util.function.Predicate;
/** Per-review worker-only native dictionary validation. Never retains context or performs IO/network. */
public final class BoundedDictionaryLexicon implements LocalContextReviewer.Lexicon {
 private final LocalContextReviewer.Lexicon base;
 private final Predicate<String> dictionary;
 private final LocalContextReviewer.Cancellation cancel;
 private final Map<String,Boolean> checked=new HashMap<>();
 public BoundedDictionaryLexicon(LocalContextReviewer.Lexicon base,Predicate<String> dictionary,LocalContextReviewer.Cancellation cancel){
  this.base=base;this.dictionary=dictionary;this.cancel=cancel;
 }
 public boolean valid(String word){
  if(cancel.cancelled())throw new java.util.concurrent.CancellationException();
  String key=word.toLowerCase(Locale.ROOT);
  if(base.preserved(key))return true;
  Boolean result=checked.get(key);
  if(result==null){
   if(checked.size()>=512)throw new java.util.concurrent.CancellationException();
   result=dictionary.test(key);checked.put(key,result);
  }
  return result;
 }
 public boolean knownInvalid(String word){return !valid(word);}
 public boolean preserved(String word){return base.preserved(word);}
 public boolean spaceNeighbor(char c){return base.spaceNeighbor(c);}
 public List<String> candidates(String word){return base.candidates(word);}
 public double prior(String source,String target){return base.prior(source,target);}
 public double personalPreference(String source,String target){return base.personalPreference(source,target);}
 public boolean rejected(String source,String target){return base.rejected(source,target);}
 public double keyDistance(char a,char b){return base.keyDistance(a,b);}
}
