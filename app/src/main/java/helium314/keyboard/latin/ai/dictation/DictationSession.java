// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import helium314.keyboard.latin.ai.EditorSnapshot;
/** Versioned recording lease, following Ramblr's recording/transcribing ownership separation. */
public final class DictationSession {
 public enum State { IDLE,RECORDING,TRANSCRIBING,REVIEW }
 private long generation;private State state=State.IDLE;private EditorSnapshot editor;private String text="";
 public synchronized long begin(EditorSnapshot snapshot,boolean permission,boolean protectedField){
  if(!permission||protectedField||snapshot==null||snapshot.editor==null||snapshot.start<0||state!=State.IDLE)return -1;
  editor=snapshot;text="";state=State.RECORDING;return ++generation;
 }
 public synchronized boolean stop(long ticket){if(ticket!=generation||state!=State.RECORDING)return false;state=State.TRANSCRIBING;return true;}
 public synchronized boolean result(long ticket,EditorSnapshot current,String transcript){
  if(ticket!=generation||state!=State.TRANSCRIBING||editor==null||!editor.matches(current)||transcript==null||transcript.trim().isEmpty()||transcript.length()>16384)return false;
  text=transcript;state=State.REVIEW;return true;
 }
 public synchronized void cancel(){generation++;state=State.IDLE;editor=null;text="";}
 public synchronized State state(){return state;}
 public synchronized String text(){return text;}
}
