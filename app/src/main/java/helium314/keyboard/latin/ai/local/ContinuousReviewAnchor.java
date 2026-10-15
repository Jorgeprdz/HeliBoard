// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
/** Append-only dependency anchor. No fuzzy matching, retained conversations or cursor guesses. */
public final class ContinuousReviewAnchor {
 public final Object editor;public final String packageName,text;public final int fieldId,start;
 public final long epoch;
 public ContinuousReviewAnchor(Object editor,String packageName,int fieldId,long epoch,int start,String text){
  this.editor=editor;this.packageName=packageName;this.fieldId=fieldId;this.epoch=epoch;this.start=start;this.text=text;
 }
 public boolean matches(Object editor,String packageName,int fieldId,long epoch,int cursorStart,int cursorEnd,String before){
  if(this.editor!=editor||!this.packageName.equals(packageName)||this.fieldId!=fieldId||this.epoch!=epoch
    ||cursorStart!=cursorEnd||cursorStart<start+text.length()||before==null)return false;
  int offset=start-(cursorStart-before.length());
  return offset>=0&&offset+text.length()<=before.length()&&before.regionMatches(offset,text,0,text.length());
 }
}
