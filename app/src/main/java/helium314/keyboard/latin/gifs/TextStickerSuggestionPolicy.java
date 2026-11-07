// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import java.util.Objects;

/** Pure privacy and exact-target checks for local, automatic text-sticker suggestions. */
public final class TextStickerSuggestionPolicy {
 private TextStickerSuggestionPolicy(){}
 public static boolean allowed(int inputType,int imeOptions,boolean incognito,boolean excluded,
         boolean suggestions,boolean ai,boolean internalPanel){
  return suggestions&&!ai&&!internalPanel&&!SensitiveInputPolicy.protectedEditor(inputType,imeOptions,incognito,excluded);
 }
 public static String source(String composing,String before,String after){
  if(before==null||after==null||SensitiveInputPolicy.sensitiveText(before+" "+after))return "";
  String text=composing==null?"":composing.trim();
  if(text.isEmpty()){
   String trimmed=before.trim();int boundary=trimmed.length();
   while(boundary>0&&!Character.isWhitespace(trimmed.charAt(boundary-1)))boundary--;
   text=trimmed.substring(boundary);
  }
  if(text.isEmpty()||text.codePointCount(0,text.length())>120||SensitiveInputPolicy.sensitiveText(text)
          ||text.matches("(?s).*[\\p{Cntrl}\\p{Cs}].*"))return "";
  return text;
 }
 public static final class Target {
  public final Object connection,editor;
  public final long generation;
  public final String packageName,before,after,text;
  public final int fieldId,inputType,imeOptions,start,end;
  public Target(Object connection,Object editor,long generation,String packageName,int fieldId,int inputType,int imeOptions,int start,int end,
                String before,String after,String text){
   this.connection=connection;this.editor=editor;this.generation=generation;this.packageName=packageName;
   this.fieldId=fieldId;this.inputType=inputType;this.imeOptions=imeOptions;this.start=start;this.end=end;this.before=before;this.after=after;this.text=text;
  }
  public boolean matches(Target other){
   return other!=null&&connection!=null&&editor!=null&&start>=0&&start==end
    &&connection==other.connection&&generation==other.generation
    &&fieldId==other.fieldId&&inputType==other.inputType&&imeOptions==other.imeOptions&&start==other.start&&end==other.end
    &&Objects.equals(packageName,other.packageName)&&Objects.equals(before,other.before)
    &&Objects.equals(after,other.after)&&Objects.equals(text,other.text);
  }
 }
}
