// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import helium314.keyboard.latin.ai.EditorSnapshot;import org.junit.Test;import static org.junit.Assert.*;
public class DictationSessionTest {
 final Object editor=new Object();
 EditorSnapshot snapshot(long revision){return new EditorSnapshot(editor,"editor",2,1,revision,10,10,"");}
 Object session(){try{return Class.forName("helium314.keyboard.latin.ai.dictation.DictationSession").getConstructor().newInstance();}catch(Exception e){throw new AssertionError("Dictation lifecycle missing",e);}}
 Object call(Object s,String name,Class<?>[] types,Object...args){try{return s.getClass().getMethod(name,types).invoke(s,args);}catch(Exception e){throw new AssertionError(e);}}
 long begin(Object s,boolean permission,boolean protectedField){return (long)call(s,"begin",new Class[]{EditorSnapshot.class,boolean.class,boolean.class},snapshot(1),permission,protectedField);}
 boolean result(Object s,long ticket,long rev){return (boolean)call(s,"result",new Class[]{long.class,EditorSnapshot.class,String.class},ticket,snapshot(rev),"Hola, Juan.");}
 @Test public void deniedPermissionCannotCapture(){assertEquals(-1,begin(session(),false,false));}
 @Test public void protectedEditorCannotCapture(){assertEquals(-1,begin(session(),true,true));}
 @Test public void stoppedRecordingProducesPreviewForSameEditor(){Object s=session();long t=begin(s,true,false);assertTrue(t>0);call(s,"stop",new Class[]{long.class},t);assertTrue(result(s,t,1));}
 @Test public void cancelledRecordingCannotDeliver(){Object s=session();long t=begin(s,true,false);call(s,"cancel",new Class[]{});assertFalse(result(s,t,1));}
 @Test public void editorChangeRejectsTranscript(){Object s=session();long t=begin(s,true,false);call(s,"stop",new Class[]{long.class},t);assertFalse(result(s,t,2));}
 @Test public void olderRecordingCannotPublishIntoNewRecording(){Object s=session();long old=begin(s,true,false);call(s,"cancel",new Class[]{});long fresh=begin(s,true,false);call(s,"stop",new Class[]{long.class},fresh);assertFalse(result(s,old,1));assertTrue(result(s,fresh,1));}
}
