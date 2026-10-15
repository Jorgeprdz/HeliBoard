// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
public final class ExplicitTextPolicy {
 private ExplicitTextPolicy(){}
 public static boolean accepts(String action,String mime,CharSequence text){return ("android.intent.action.SEND".equals(action)||"android.intent.action.PROCESS_TEXT".equals(action))&&"text/plain".equals(mime)&&text!=null&&text.length()>0&&text.length()<=4096;}
 public static boolean mayReturn(String action,boolean readOnly){return "android.intent.action.PROCESS_TEXT".equals(action)&&!readOnly;}
}
