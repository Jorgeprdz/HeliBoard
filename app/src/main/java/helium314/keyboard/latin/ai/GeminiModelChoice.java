// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.util.*;

/** Choose only from models the configured key actually advertises. */
public final class GeminiModelChoice {
    private GeminiModelChoice() {}
    public static String choose(String[] models) {
        if(models==null)return "";
        List<String> candidates=new ArrayList<>();
        for(String name:models){
            if(name==null)continue;
            String id=name.startsWith("models/")?name.substring(7):name;
            if(id.matches("gemini-[A-Za-z0-9._-]+")&&id.contains("flash")
                &&!id.matches(".*(image|tts|audio|live|native|robot|computer|embedding).*"))candidates.add(id);
        }
        candidates.sort(Comparator.<String>comparingInt(id->id.matches(".*(preview|exp).*" )?1:0)
            .thenComparingInt(id->id.contains("lite")?1:0)
            .thenComparing(Comparator.reverseOrder()));
        return candidates.isEmpty()?"":candidates.get(0);
    }
}
