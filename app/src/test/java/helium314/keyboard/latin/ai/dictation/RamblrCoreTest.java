// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import org.junit.Test;import static org.junit.Assert.*;
public class RamblrCoreTest {
 private Class<?> type(String name){try{return Class.forName("helium314.keyboard.latin.ai.dictation.ramblr."+name);}catch(Exception e){throw new AssertionError("Ramblr core not integrated",e);}}
 @Test public void recordingTransitionsHaveSingleOwner() throws Exception {Class<?> c=type("RecordingStateMachine");Object state=c.getConstructor().newInstance();assertEquals(true,c.getMethod("tryStartRecording").invoke(state));assertEquals(false,c.getMethod("tryStartRecording").invoke(state));assertEquals(true,c.getMethod("tryStartTranscribing").invoke(state));assertEquals(false,c.getMethod("tryStartTranscribing").invoke(state));c.getMethod("reset").invoke(state);assertEquals(true,c.getMethod("tryStartRecording").invoke(state));}
 @Test public void wavHeaderHasCorrectPcmContainer() throws Exception {Class<?> c=type("WavWriter");byte[] header=(byte[])c.getMethod("header",long.class,int.class,int.class,int.class).invoke(c.getField("INSTANCE").get(null),32000L,16000,1,16);assertEquals(44,header.length);assertEquals("RIFF",new String(header,0,4,java.nio.charset.StandardCharsets.US_ASCII));assertEquals("WAVE",new String(header,8,4,java.nio.charset.StandardCharsets.US_ASCII));assertEquals(32000,java.nio.ByteBuffer.wrap(header,40,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt());}
}
