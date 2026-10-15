// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;
/** Adds dictionary languages only; never selects a different keyboard layout or subtype. */
public final class BilingualLocalePolicy {
 private BilingualLocalePolicy(){}
 public static List<Locale> locales(List<Locale> configured,boolean enabled){
  if(configured==null||configured.isEmpty())return Collections.emptyList();
  LinkedHashSet<Locale> result=new LinkedHashSet<>(configured);
  String main=configured.get(0).getLanguage();
  if(enabled&&(main.equals("es")||main.equals("en"))){boolean es=false,en=false;for(Locale l:configured){es|=l.getLanguage().equals("es");en|=l.getLanguage().equals("en");}if(!es)result.add(new Locale("es","MX"));if(!en)result.add(Locale.US);}
  return Collections.unmodifiableList(new ArrayList<>(result));
 }
}
