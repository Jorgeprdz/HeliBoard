// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.util.*;
import java.util.regex.*;
/** Complete result, explicit review and unchanged editor are independent acceptance conditions. */
public final class AiResultReview {
 private EditorSnapshot snapshot;private String text="";private boolean complete,reviewed,risk;
 public void offer(EditorSnapshot target,String value,boolean finished){clear();if(target==null||value==null||value.isEmpty()||value.length()>16384)return;
  snapshot=target;text=value;complete=finished;
  risk=!tokens(target.original,"[0-9]+(?:[.,][0-9]+)*").equals(tokens(value,"[0-9]+(?:[.,][0-9]+)*"))
    ||!tokens(target.original,"\\b(?:no|not|never|nunca|jamás|neither|don't|can't|won't)\\b").equals(tokens(value,"\\b(?:no|not|never|nunca|jamás|neither|don't|can't|won't)\\b"));
 }
 private static List<String> tokens(CharSequence text,String regex){List<String> result=new ArrayList<>();if(text==null)return result;Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);while(m.find())result.add(m.group().toLowerCase(Locale.ROOT));return result;}
 public boolean reviewed(EditorSnapshot current){reviewed=snapshot!=null&&snapshot.matches(current);return reviewed;}
 public boolean canApply(EditorSnapshot current){return complete&&reviewed&&snapshot!=null&&snapshot.matches(current);}
 public String result(){return text;}
 public EditorSnapshot snapshot(){return snapshot;}
 public boolean meaningRisk(){return risk;}
 public void clear(){snapshot=null;text="";complete=false;reviewed=false;risk=false;}
}
