// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import helium314.keyboard.latin.ai.SensitiveInputPolicy;import java.util.*;
public final class DictationOptions {
 private DictationOptions(){}
 public static List<String> vocabulary(String text){if(text==null||text.isEmpty())return Collections.emptyList();if(text.length()>2048)throw new IllegalArgumentException("Vocabulary limit");LinkedHashSet<String> words=new LinkedHashSet<>();Set<String> seen=new HashSet<>();for(String raw:text.split("[,;\\n]")){String word=raw.trim();if(word.isEmpty())continue;if(word.length()>48||SensitiveInputPolicy.sensitiveText(word))throw new IllegalArgumentException("Unsafe vocabulary");if(seen.add(word.toLowerCase(Locale.ROOT)))words.add(word);if(words.size()>32)throw new IllegalArgumentException("Vocabulary limit");}return Collections.unmodifiableList(new ArrayList<>(words));}
}
