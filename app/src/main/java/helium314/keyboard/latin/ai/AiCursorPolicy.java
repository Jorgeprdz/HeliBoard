// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
public final class AiCursorPolicy {
 private AiCursorPolicy(){}
 public static int afterReplacement(int oldStart,int oldEnd,int start,int end,int replacementLength){
  if(oldStart==oldEnd&&end<=oldStart)return oldStart+replacementLength-(end-start);
  return start+replacementLength;
 }
}
