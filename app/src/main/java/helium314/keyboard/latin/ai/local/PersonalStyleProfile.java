// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import java.util.*;

/** Optional aggregate style features, not semantic understanding. No phrases or arbitrary
 * vocabulary are retained. Register detection is deliberately a small, inspectable heuristic.
 */
public final class PersonalStyleProfile {
 private static final Set<String> CASUAL=new HashSet<>(Arrays.asList("oye","hola","hey","hi","pues"));
 private static final Set<String> PROFESSIONAL=new HashSet<>(Arrays.asList("estimado","estimada","saludos","asimismo","cordialmente","regards","therefore","however"));
 private static final Set<String> CONNECTORS=new HashSet<>(Arrays.asList("además","entonces","pero","asimismo","aunque","porque","however","also","therefore","because"));
 public static final Set<String> KEYS;
 static{
  Set<String> k=new HashSet<>(Arrays.asList("register.casual","register.professional","sentence.short","sentence.medium","sentence.long","punctuation.period","punctuation.question","punctuation.exclamation","emoji","language.es","language.en","language.mixed","opening.casual","opening.professional","clause.coordination","clause.subordination","ending.question","ending.exclamation","ending.ellipsis"));
  for(String word:CONNECTORS)k.add("connector."+word);
  KEYS=Collections.unmodifiableSet(k);
 }
 private PersonalStyleProfile(){}
 public static Map<String,Long> features(String text,String language){return features(text,language,false);}
 public static Map<String,Long> features(String text,String language,boolean messageOpening){
  Map<String,Long> out=new HashMap<>();
  if(text==null||text.length()>512||SensitiveInputPolicy.sensitiveText(text)||text.matches("(?s).*[@/\\\\=<>_].*"))return out;
  String[] words=text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{M}]+");int length=0;boolean es=false,en=false;
  String first="";
  for(String word:words){if(word.isEmpty())continue;if(first.isEmpty())first=word;length++;
   if(Arrays.asList("and","but","y","pero").contains(word))out.put("clause.coordination",1L);
   if(Arrays.asList("because","although","porque","aunque").contains(word))out.put("clause.subordination",1L);
   if(CASUAL.contains(word))out.put("register.casual",1L);
   if(PROFESSIONAL.contains(word))out.put("register.professional",1L);
   if(CONNECTORS.contains(word))out.put("connector."+word,1L);
   if(Arrays.asList("el","la","que","de","oye","además","pero").contains(word))es=true;
   if(Arrays.asList("the","and","that","with","hey","however","because").contains(word))en=true;
  }
  if(length==0)return out;
  if(messageOpening){
   if(CASUAL.contains(first))out.put("opening.casual",1L);
   if(PROFESSIONAL.contains(first))out.put("opening.professional",1L);
  }
  String trimmed=text.trim();
  if(trimmed.endsWith("?"))out.put("ending.question",1L);
  if(trimmed.endsWith("!"))out.put("ending.exclamation",1L);
  if(trimmed.endsWith("...")||trimmed.endsWith("…"))out.put("ending.ellipsis",1L);
  out.put(length<=8?"sentence.short":length<=20?"sentence.medium":"sentence.long",1L);
  if(text.indexOf('?')>=0)out.put("punctuation.question",1L);
  if(text.indexOf('!')>=0)out.put("punctuation.exclamation",1L);
  if(text.indexOf('.')>=0)out.put("punctuation.period",1L);
  if(text.codePoints().anyMatch(c->c>=0x1F300&&c<=0x1FAFF))out.put("emoji",1L);
  if("es-MX".equals(language))out.put("language.es",1L);
  if("en-US".equals(language))out.put("language.en",1L);
  if(es&&en)out.put("language.mixed",1L);
  return out;
 }
 public static String register(String context){
  if(context==null)return "";
  Set<String> words=new HashSet<>(Arrays.asList(context.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{M}]+")));
  if(!Collections.disjoint(words,PROFESSIONAL))return "professional";
  return !Collections.disjoint(words,CASUAL)?"casual":"";
 }
 /** Current explicit context always wins over aggregate habit. Sparse/tied history abstains. */
 public static String preferredRegister(String context,Map<String,Long> traits){
  String current=register(context);if(!current.isEmpty())return current;
  long casual=Math.max(0,traits.getOrDefault("register.casual",0L));
  long professional=Math.max(0,traits.getOrDefault("register.professional",0L));
  if(casual+professional<5||Math.abs(casual-professional)<3)return "";
  return casual>professional?"casual":"professional";
 }
 public static double preference(String word,String register,Map<String,Long> traits){
  return preference(word,register,traits,false);
 }
 public static double preference(String word,String register,Map<String,Long> traits,boolean messageOpening){
  String w=word.toLowerCase(Locale.ROOT);double score=0;
  if("professional".equals(register))score+=PROFESSIONAL.contains(w)?.15:CASUAL.contains(w)?-.15:0;
  if("casual".equals(register))score+=CASUAL.contains(w)?.15:PROFESSIONAL.contains(w)?-.15:0;
  if(messageOpening&&CASUAL.contains(w)&&!"professional".equals(register))score+=Math.min(.12,Math.log1p(traits.getOrDefault("opening.casual",0L))*.04);
  if(messageOpening&&PROFESSIONAL.contains(w)&&!"casual".equals(register))score+=Math.min(.12,Math.log1p(traits.getOrDefault("opening.professional",0L))*.04);
  if(CONNECTORS.contains(w))score+=Math.min(.2,Math.log1p(traits.getOrDefault("connector."+w,0L))*.06);
  return score;
 }
}
